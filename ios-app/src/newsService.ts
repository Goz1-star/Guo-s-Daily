export type NewsArticle = {
  id: string;
  category: 'AI' | '科技';
  region: '国内' | '国际';
  source: string;
  sourceLabel: string;
  publishedAt: string;
  title: string;
  translation?: string;
  summary: string;
  reason: string;
  impact: number;
  heat: number;
  url: string;
  isBrief?: boolean;
};

type SourceItem = {
  title?: string;
  title_en?: string;
  title_zh?: string;
  summary?: string | null;
  recommend_reason_zh?: string;
  url?: string;
  source?: string;
  source_name?: string;
  published_at?: string;
};

type FeedStory = {
  story_id?: string;
  title?: string;
  url?: string;
  primary_url?: string;
  source?: string;
  source_name?: string;
  sources?: SourceItem[];
  primary_item?: SourceItem;
  source_count?: number;
  importance?: number;
  importance_score?: number;
  latest_at?: string;
  persona_review?: string;
  reasons?: string[];
  category?: string;
  importance_breakdown?: {
    recency?: number;
    story_heat?: number;
  };
};

type DailyBriefResponse = {
  generated_at?: string;
  items?: FeedStory[];
};

const DAILY_BRIEF_URL = 'https://news.learnprompt.pro/data/daily-brief.json';

function clamp(value: number, minimum: number, maximum: number) {
  return Math.min(maximum, Math.max(minimum, value));
}

function formatPublishedAt(value?: string) {
  if (!value) return '时间待核验';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const startOfStoryDay = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const dayDifference = Math.round((startOfToday - startOfStoryDay) / 86_400_000);
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;

  if (dayDifference === 0) return `今天 ${time}`;
  if (dayDifference === 1) return `昨天 ${time}`;
  return `${date.getMonth() + 1}月${date.getDate()}日 ${time}`;
}

function classifyRegion(story: FeedStory, primary: SourceItem): '国内' | '国际' {
  const searchable = [
    story.primary_url,
    story.url,
    story.source,
    primary.source,
    primary.title_zh,
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
  const domesticSignals = [
    '.cn',
    'ithome',
    '36kr',
    'baidu',
    'alibaba',
    'tencent',
    'bytedance',
    '字节',
    '百度',
    '阿里',
    '腾讯',
    '华为',
    '智谱',
    '月之暗面',
    '蚂蚁',
    '公众号',
    'it之家',
  ];
  return domesticSignals.some((signal) => searchable.includes(signal)) ? '国内' : '国际';
}

function classifyCategory(story: FeedStory, primary: SourceItem): 'AI' | '科技' {
  const searchable = [story.title, primary.title, primary.title_zh, primary.summary]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
  const technologySignals = [
    'chip',
    'semiconductor',
    'robot',
    'hardware',
    'device',
    '芯片',
    '半导体',
    '机器人',
    '硬件',
    '设备',
  ];
  return technologySignals.some((signal) => searchable.includes(signal)) ? '科技' : 'AI';
}

function reasonFromSignals(story: FeedStory) {
  const labels: string[] = [];
  if (story.reasons?.includes('multi_source')) labels.push('多个来源共同报道');
  if (story.reasons?.includes('high_importance')) labels.push('影响力评分较高');
  if (story.reasons?.includes('high_ai_relevance')) labels.push('与 AI 高度相关');
  return labels.length > 0 ? `${labels.join('，')}。` : '在最新 AI 与科技信息流中具有较高影响力。';
}

function toArticle(story: FeedStory, index: number): NewsArticle | null {
  const primary = story.primary_item ?? story.sources?.[0] ?? {};
  const rawTitle = primary.title ?? story.title ?? '';
  const titleEn = primary.title_en?.trim() ?? '';
  const titleZh = primary.title_zh?.trim() ?? '';
  const title = titleEn || rawTitle || titleZh;
  const url = story.primary_url ?? primary.url ?? story.url ?? '';
  if (!title || !url) return null;

  const score = story.importance ?? story.importance_score ?? 0.65;
  const sourceCount = Math.max(1, story.source_count ?? story.sources?.length ?? 1);
  const storyHeat = story.importance_breakdown?.story_heat ?? 0.35;
  const recency = story.importance_breakdown?.recency ?? 0.7;
  const impact = clamp(Math.round(score * 100), 45, 99);
  const heat = clamp(Math.round(45 + storyHeat * 35 + recency * 12 + sourceCount * 2), 45, 99);
  const translation = titleEn && titleZh && titleZh !== titleEn ? titleZh : undefined;

  return {
    id: story.story_id ?? `${index}-${url}`,
    category: classifyCategory(story, primary),
    region: classifyRegion(story, primary),
    source: primary.source ?? story.source ?? primary.source_name ?? 'AI News Radar',
    sourceLabel: primary.source_name ?? story.source_name ?? 'AI News Radar',
    publishedAt: formatPublishedAt(story.latest_at ?? primary.published_at),
    title,
    translation,
    summary:
      primary.summary?.trim() ||
      '原始来源暂未提供摘要，请打开原文核验具体内容。',
    reason:
      primary.recommend_reason_zh?.trim() ||
      story.persona_review?.trim() ||
      reasonFromSignals(story),
    impact,
    heat,
    url,
    isBrief: index >= 10,
  };
}

export async function fetchLatestNews() {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 20_000);

  try {
    const response = await fetch(`${DAILY_BRIEF_URL}?refresh=${Date.now()}`, {
      headers: {
        Accept: 'application/json',
      },
      cache: 'no-store',
      signal: controller.signal,
    });
    if (!response.ok) {
      throw new Error(`新闻源返回 HTTP ${response.status}`);
    }

    const payload = (await response.json()) as DailyBriefResponse;
    const unique = new Map<string, NewsArticle>();
    (payload.items ?? []).forEach((story, index) => {
      const article = toArticle(story, index);
      if (!article) return;
      const key = article.url || article.title.toLowerCase();
      const existing = unique.get(key);
      if (!existing || article.impact > existing.impact) unique.set(key, article);
    });

    const articles = [...unique.values()]
      .sort((left, right) => right.impact - left.impact || right.heat - left.heat)
      .slice(0, 10);
    if (articles.length === 0) {
      throw new Error('新闻源暂时没有返回有效内容');
    }

    return {
      articles,
      generatedAt: payload.generated_at ?? new Date().toISOString(),
    };
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      throw new Error('连接新闻源超时，请稍后重试');
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}
