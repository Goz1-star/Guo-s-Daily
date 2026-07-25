import { StatusBar } from 'expo-status-bar';
import * as SecureStore from 'expo-secure-store';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  ActivityIndicator,
  Linking,
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  Share,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  useColorScheme,
  View,
} from 'react-native';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchLatestNews, type NewsArticle } from './src/newsService';

type Tab = 'today' | 'saved' | 'history' | 'settings';
type ThemeMode = 'system' | 'light' | 'dark';
type ApiOperationState = 'idle' | 'saving' | 'saved' | 'testing' | 'success' | 'error';

const DEEPSEEK_KEY_STORE = 'deepseek_api_key';
const NEWS_CACHE_STORE = 'guo_daily_latest_news';
const NEWS_CACHE_TIME_STORE = 'guo_daily_latest_news_time';

type Article = NewsArticle;

const demoArticles: Article[] = [
  {
    id: 'openai-agents',
    category: 'AI',
    region: '国际',
    source: 'news.learnprompt.pro',
    sourceLabel: 'Learn Prompt',
    publishedAt: '今天 06:42',
    title: 'The next phase of AI is being built around agents',
    translation: 'AI 的下一阶段，正在围绕智能体展开',
    summary:
      '从单轮问答走向可执行任务，AI 智能体开始连接工具、数据与工作流。行业竞争的重点也逐渐从模型参数转向可靠性与实际产出。',
    reason: '涉及 AI 产品形态与产业链迁移，可能影响接下来一年的应用开发方式。',
    impact: 94,
    heat: 91,
    url: 'https://news.learnprompt.pro/',
  },
  {
    id: 'agents-radar',
    category: 'AI',
    region: '国际',
    source: 'duanyytop/agents-radar',
    sourceLabel: 'Agents Radar',
    publishedAt: '今天 05:18',
    title: 'Open-source agent tools are becoming the new developer layer',
    translation: '开源智能体工具正在成为新的开发者基础层',
    summary:
      '开源项目持续补齐记忆、工具调用和多智能体协作能力，开发者可以用更低成本搭建面向真实任务的 AI 工作流。',
    reason: '开源生态的活跃度较高，且与个人开发者的工具选择直接相关。',
    impact: 88,
    heat: 87,
    url: 'https://duanyytop.github.io/agents-radar/',
  },
  {
    id: 'ai-chips',
    category: '科技',
    region: '国际',
    source: 'The New York Times',
    sourceLabel: 'NYTimes Technology',
    publishedAt: '昨天 23:40',
    title: 'The race to build smaller, faster AI chips',
    translation: '更小、更快的 AI 芯片竞赛正在加速',
    summary:
      '产业界正在通过专用架构、先进封装与边缘计算降低 AI 推理成本。芯片效率将继续影响模型能否进入更多终端设备。',
    reason: '芯片是 AI 规模化落地的底层约束，影响范围覆盖云端和消费电子。',
    impact: 85,
    heat: 82,
    url: 'https://www.nytimes.com/section/technology',
  },
  {
    id: 'china-models',
    category: 'AI',
    region: '国内',
    source: '科技媒体综合',
    sourceLabel: '国内科技',
    publishedAt: '昨天 20:15',
    title: '中国企业加速把大模型接入实际业务',
    summary:
      '从客服、研发到内容生产，更多企业开始用可衡量的业务指标评估大模型价值，应用重点从展示能力转向节省时间与成本。',
    reason: '国内企业应用进入规模化验证阶段，具有较强的行业代表性。',
    impact: 81,
    heat: 78,
    url: 'https://news.learnprompt.pro/',
  },
  {
    id: 'brief-edge',
    category: '科技',
    region: '国际',
    source: '行业简讯',
    sourceLabel: 'Guo 的日报',
    publishedAt: '昨天 18:30',
    title: 'Edge AI continues to move from demos to devices',
    translation: '边缘 AI 正从演示走向更多真实设备',
    summary: '一句话简讯：端侧推理、低功耗芯片与隐私需求共同推动 AI 下沉到设备。',
    reason: '作为不足十条时的补充简讯，帮助保持日报的信息完整度。',
    impact: 68,
    heat: 65,
    url: 'https://news.learnprompt.pro/',
    isBrief: true,
  },
];

const palette = {
  light: {
    background: '#F7F6F2',
    surface: '#FFFFFF',
    surfaceMuted: '#EEEDE8',
    text: '#171717',
    textMuted: '#6B6A66',
    border: '#D9D7D0',
    accent: '#B23A2B',
    accentSoft: '#F3DDD7',
    bar: '#171717',
    tab: '#77756E',
  },
  dark: {
    background: '#111211',
    surface: '#1B1C1A',
    surfaceMuted: '#272824',
    text: '#F3F1EA',
    textMuted: '#AAA9A1',
    border: '#3A3B36',
    accent: '#E26B55',
    accentSoft: '#482A25',
    bar: '#F3F1EA',
    tab: '#B9B7AF',
  },
} as const;

function todayLabel() {
  const now = new Date();
  return `${now.getMonth() + 1}月${now.getDate()}日 · 07:30`;
}

function feedTimeLabel(value: string | null) {
  if (!value) return '等待首次联网更新';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '已读取最新数据';
  return `${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(
    date.getMinutes(),
  ).padStart(2, '0')} 更新`;
}

function App() {
  const systemScheme = useColorScheme();
  const [tab, setTab] = useState<Tab>('today');
  const [themeMode, setThemeMode] = useState<ThemeMode>('system');
  const [selectedArticle, setSelectedArticle] = useState<Article | null>(null);
  const [savedIds, setSavedIds] = useState<string[]>([]);
  const [readIds, setReadIds] = useState<string[]>([]);
  const [notificationsEnabled, setNotificationsEnabled] = useState(true);
  const [apiKeyConfigured, setApiKeyConfigured] = useState(false);
  const [apiOperation, setApiOperation] = useState<ApiOperationState>('idle');
  const [apiMessage, setApiMessage] = useState('尚未配置 DeepSeek API Key');
  const [newsArticles, setNewsArticles] = useState<Article[]>(demoArticles);
  const [newsRefreshing, setNewsRefreshing] = useState(true);
  const [newsError, setNewsError] = useState<string | null>(null);
  const [newsGeneratedAt, setNewsGeneratedAt] = useState<string | null>(null);

  const isDark = themeMode === 'dark' || (themeMode === 'system' && systemScheme === 'dark');
  const colors = isDark ? palette.dark : palette.light;
  const savedArticles = useMemo(
    () => newsArticles.filter((article) => savedIds.includes(article.id)),
    [newsArticles, savedIds],
  );

  const refreshNews = useCallback(async () => {
    setNewsRefreshing(true);
    setNewsError(null);
    try {
      const latest = await fetchLatestNews();
      setNewsArticles(latest.articles);
      setNewsGeneratedAt(latest.generatedAt);
      await AsyncStorage.multiSet([
        [NEWS_CACHE_STORE, JSON.stringify(latest.articles)],
        [NEWS_CACHE_TIME_STORE, latest.generatedAt],
      ]);
    } catch (error) {
      setNewsError(error instanceof Error ? error.message : '网络异常，请稍后重试');
    } finally {
      setNewsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    let active = true;

    AsyncStorage.multiGet([NEWS_CACHE_STORE, NEWS_CACHE_TIME_STORE])
      .then((entries) => {
        if (!active) return;
        const cachedNews = entries[0]?.[1];
        const cachedTime = entries[1]?.[1];
        if (cachedNews) {
          const parsed = JSON.parse(cachedNews) as Article[];
          if (Array.isArray(parsed) && parsed.length > 0) setNewsArticles(parsed);
        }
        if (cachedTime) setNewsGeneratedAt(cachedTime);
      })
      .catch(() => {
        // 缓存不可用不妨碍联网刷新。
      })
      .finally(() => {
        if (active) void refreshNews();
      });

    return () => {
      active = false;
    };
  }, [refreshNews]);

  useEffect(() => {
    if (Platform.OS === 'web') {
      setApiMessage('请在 iPhone 安装版中保存 API Key');
      return;
    }

    let active = true;
    SecureStore.getItemAsync(DEEPSEEK_KEY_STORE)
      .then((storedKey) => {
        if (!active) return;
        const configured = Boolean(storedKey);
        setApiKeyConfigured(configured);
        setApiMessage(configured ? 'API Key 已安全保存在本机' : '尚未配置 DeepSeek API Key');
      })
      .catch(() => {
        if (!active) return;
        setApiOperation('error');
        setApiMessage('读取本机 API Key 失败');
      });

    return () => {
      active = false;
    };
  }, []);

  const toggleSaved = (articleId: string) => {
    setSavedIds((current) =>
      current.includes(articleId)
        ? current.filter((id) => id !== articleId)
        : [...current, articleId],
    );
  };

  const openArticle = (article: Article) => {
    setReadIds((current) => (current.includes(article.id) ? current : [...current, article.id]));
    setSelectedArticle(article);
  };

  const shareArticle = async (article: Article) => {
    await Share.share({
      title: article.title,
      message: `${article.title}\n${article.translation ?? ''}\n\n${article.summary}\n\n${article.url}`,
    });
  };

  const saveDeepSeekApiKey = async (apiKey: string) => {
    const normalizedKey = apiKey.trim();
    if (!normalizedKey.startsWith('sk-') || normalizedKey.length < 20) {
      setApiOperation('error');
      setApiMessage('Key 格式不正确，应以 sk- 开头');
      return false;
    }
    if (Platform.OS === 'web') {
      setApiOperation('error');
      setApiMessage('Web 预览不会保存密钥，请在 iPhone 安装版中操作');
      return false;
    }

    setApiOperation('saving');
    setApiMessage('正在安全保存…');
    try {
      await SecureStore.setItemAsync(DEEPSEEK_KEY_STORE, normalizedKey, {
        keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
      });
      setApiKeyConfigured(true);
      setApiOperation('saved');
      setApiMessage('API Key 已保存到 iPhone 钥匙串');
      return true;
    } catch {
      setApiOperation('error');
      setApiMessage('保存失败，请稍后重试');
      return false;
    }
  };

  const deleteDeepSeekApiKey = async () => {
    if (Platform.OS === 'web') {
      setApiOperation('error');
      setApiMessage('Web 预览中没有保存 API Key');
      return;
    }

    try {
      await SecureStore.deleteItemAsync(DEEPSEEK_KEY_STORE);
      setApiKeyConfigured(false);
      setApiOperation('idle');
      setApiMessage('API Key 已从本机删除');
    } catch {
      setApiOperation('error');
      setApiMessage('删除失败，请稍后重试');
    }
  };

  const testDeepSeekConnection = async () => {
    if (Platform.OS === 'web') {
      setApiOperation('error');
      setApiMessage('请在 iPhone 安装版中测试连接');
      return;
    }

    const storedKey = await SecureStore.getItemAsync(DEEPSEEK_KEY_STORE);
    if (!storedKey) {
      setApiKeyConfigured(false);
      setApiOperation('error');
      setApiMessage('请先保存 API Key');
      return;
    }

    setApiOperation('testing');
    setApiMessage('正在连接 DeepSeek…');
    try {
      const response = await fetch('https://api.deepseek.com/chat/completions', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${storedKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          model: 'deepseek-v4-pro',
          messages: [{ role: 'user', content: '请只回复 OK' }],
          max_tokens: 8,
          thinking: { type: 'disabled' },
        }),
      });
      const payload = (await response.json()) as {
        choices?: Array<{ message?: { content?: string } }>;
        error?: { message?: string };
      };

      if (!response.ok) {
        setApiOperation('error');
        setApiMessage(`连接失败（${response.status}）：${payload.error?.message ?? '请检查 Key'}`);
        return;
      }

      setApiOperation('success');
      setApiMessage(`连接成功 · ${payload.choices?.[0]?.message?.content?.trim() || 'DeepSeek 已响应'}`);
    } catch (error) {
      setApiOperation('error');
      setApiMessage(`连接失败：${error instanceof Error ? error.message : '网络异常'}`);
    }
  };

  if (selectedArticle) {
    return (
      <ArticleDetail
        article={selectedArticle}
        colors={colors}
        isDark={isDark}
        isSaved={savedIds.includes(selectedArticle.id)}
        onBack={() => setSelectedArticle(null)}
        onShare={() => shareArticle(selectedArticle)}
        onToggleSaved={() => toggleSaved(selectedArticle.id)}
      />
    );
  }

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: colors.background }]}>
      <StatusBar style={isDark ? 'light' : 'dark'} />
      <View style={styles.appFrame}>
        {tab === 'today' && (
          <TodayScreen
            articles={newsArticles}
            colors={colors}
            isDark={isDark}
            readIds={readIds}
            savedIds={savedIds}
            refreshing={newsRefreshing}
            errorMessage={newsError}
            generatedAt={newsGeneratedAt}
            onRefresh={refreshNews}
            onOpenArticle={openArticle}
            onShare={shareArticle}
            onToggleSaved={toggleSaved}
          />
        )}
        {tab === 'saved' && (
          <SavedScreen
            articles={savedArticles}
            colors={colors}
            isDark={isDark}
            onOpenArticle={openArticle}
            onToggleSaved={toggleSaved}
          />
        )}
        {tab === 'history' && (
          <HistoryScreen
            articles={newsArticles}
            colors={colors}
            isDark={isDark}
            onOpenArticle={openArticle}
          />
        )}
        {tab === 'settings' && (
          <SettingsScreen
            colors={colors}
            isDark={isDark}
            mode={themeMode}
            notificationsEnabled={notificationsEnabled}
            apiKeyConfigured={apiKeyConfigured}
            apiOperation={apiOperation}
            apiMessage={apiMessage}
            onChangeMode={setThemeMode}
            onChangeNotifications={setNotificationsEnabled}
            onSaveApiKey={saveDeepSeekApiKey}
            onDeleteApiKey={deleteDeepSeekApiKey}
            onTestApiKey={testDeepSeekConnection}
          />
        )}
      </View>
      <BottomTabs activeTab={tab} colors={colors} onChange={setTab} />
    </SafeAreaView>
  );
}

type Colors = {
  [Key in keyof typeof palette.light]: string;
};

function Masthead({ colors, eyebrow = 'PERSONAL NEWS BRIEF' }: { colors: Colors; eyebrow?: string }) {
  return (
    <View style={[styles.masthead, { borderBottomColor: colors.border }]}>
      <View>
        <Text style={[styles.eyebrow, { color: colors.accent }]}>{eyebrow}</Text>
        <Text style={[styles.wordmark, { color: colors.text }]}>Guo 的日报</Text>
      </View>
      <View style={styles.mastheadDate}>
        <Text style={[styles.dateLabel, { color: colors.textMuted }]}>{todayLabel()}</Text>
        <View style={[styles.liveDot, { backgroundColor: colors.accent }]} />
      </View>
    </View>
  );
}

function TodayScreen({
  articles: storyList,
  colors,
  isDark,
  savedIds,
  readIds,
  refreshing,
  errorMessage,
  generatedAt,
  onRefresh,
  onOpenArticle,
  onShare,
  onToggleSaved,
}: {
  articles: Article[];
  colors: Colors;
  isDark: boolean;
  savedIds: string[];
  readIds: string[];
  refreshing: boolean;
  errorMessage: string | null;
  generatedAt: string | null;
  onRefresh: () => void;
  onOpenArticle: (article: Article) => void;
  onShare: (article: Article) => void;
  onToggleSaved: (articleId: string) => void;
}) {
  const lead = storyList[0];
  const remaining = storyList.slice(1);

  return (
    <ScrollView
      contentContainerStyle={styles.scrollContent}
      showsVerticalScrollIndicator={false}
      scrollEventThrottle={16}
    >
      <Masthead colors={colors} />
      <View style={styles.sectionIntro}>
        <View>
          <Text style={[styles.sectionKicker, { color: colors.textMuted }]}>DAILY EDITION</Text>
          <Text style={[styles.sectionTitle, { color: colors.text }]}>今天值得知道的事</Text>
        </View>
        <Pressable
          accessibilityLabel="刷新日报"
          accessibilityRole="button"
          disabled={refreshing}
          onPress={onRefresh}
          style={({ pressed }) => [styles.refreshButton, pressed && styles.pressed]}
        >
          {refreshing && <ActivityIndicator color={colors.accent} size="small" />}
          <Text style={[styles.refreshText, { color: colors.accent }]}>
            {refreshing ? '更新中' : '刷新'}
          </Text>
        </Pressable>
      </View>

      <View style={[styles.editionNote, { backgroundColor: colors.surfaceMuted }]}>
        <Text style={[styles.editionNoteText, { color: colors.textMuted }]}>
          {feedTimeLabel(generatedAt)} · AI 与科技 · 影响力优先 · {storyList.length} 条入选
        </Text>
      </View>

      {errorMessage && (
        <View
          accessibilityRole="alert"
          style={[
            styles.newsErrorBanner,
            { backgroundColor: colors.accentSoft, borderColor: colors.accent },
          ]}
        >
          <Text style={[styles.newsErrorTitle, { color: colors.text }]}>暂时无法取得最新资讯</Text>
          <Text style={[styles.newsErrorBody, { color: colors.textMuted }]}>
            {errorMessage}。已保留上次成功内容，你可以稍后再次刷新。
          </Text>
        </View>
      )}

      {lead && (
        <LeadCard
          article={lead}
          colors={colors}
          isDark={isDark}
          isRead={readIds.includes(lead.id)}
          isSaved={savedIds.includes(lead.id)}
          onOpen={() => onOpenArticle(lead)}
          onShare={() => onShare(lead)}
          onToggleSaved={() => onToggleSaved(lead.id)}
        />
      )}

      <View style={styles.listHeader}>
        <Text style={[styles.listHeaderText, { color: colors.text }]}>更多重要新闻</Text>
        <Text style={[styles.listHeaderMeta, { color: colors.textMuted }]}>按影响力排序</Text>
      </View>

      {remaining.map((article, index) => (
        <StoryCard
          key={article.id}
          article={article}
          colors={colors}
          isRead={readIds.includes(article.id)}
          isSaved={savedIds.includes(article.id)}
          index={index + 2}
          onOpen={() => onOpenArticle(article)}
          onToggleSaved={() => onToggleSaved(article.id)}
        />
      ))}

      <View style={[styles.footerNote, { borderTopColor: colors.border }]}>
        <Text style={[styles.footerNoteTitle, { color: colors.text }]}>编辑说明</Text>
        <Text style={[styles.footerNoteBody, { color: colors.textMuted }]}>
          AI 仅用于翻译、摘要、去重与排序；原文链接保留，重要结论请回到来源核验。
        </Text>
      </View>
    </ScrollView>
  );
}

function LeadCard({
  article,
  colors,
  isDark,
  isRead,
  isSaved,
  onOpen,
  onShare,
  onToggleSaved,
}: {
  article: Article;
  colors: Colors;
  isDark: boolean;
  isRead: boolean;
  isSaved: boolean;
  onOpen: () => void;
  onShare: () => void;
  onToggleSaved: () => void;
}) {
  return (
    <View style={[styles.leadCard, { backgroundColor: colors.surface, borderColor: colors.border }]}>
      <Pressable onPress={onOpen} style={({ pressed }) => [pressed && styles.pressed]}>
        <View style={styles.cardMetaRow}>
          <View style={styles.metaLeft}>
            <Text style={[styles.cardKicker, { color: colors.accent }]}>01 · {article.category}</Text>
            <Text style={[styles.cardRegion, { color: colors.textMuted }]}>{article.region}</Text>
          </View>
          {isRead && <Text style={[styles.readLabel, { color: colors.textMuted }]}>已读</Text>}
        </View>
        <Text style={[styles.leadTitle, { color: colors.text }]}>{article.title}</Text>
        {article.translation && (
          <Text style={[styles.leadTranslation, { color: colors.textMuted }]}>
            {article.translation}
          </Text>
        )}
        <Text style={[styles.leadSummary, { color: colors.textMuted }]}>{article.summary}</Text>
        <MetricBars article={article} colors={colors} compact={false} />
      </Pressable>
      <CardActions
        article={article}
        colors={colors}
        isSaved={isSaved}
        onShare={onShare}
        onToggleSaved={onToggleSaved}
      />
    </View>
  );
}

function StoryCard({
  article,
  colors,
  isRead,
  isSaved,
  index,
  onOpen,
  onToggleSaved,
}: {
  article: Article;
  colors: Colors;
  isRead: boolean;
  isSaved: boolean;
  index: number;
  onOpen: () => void;
  onToggleSaved: () => void;
}) {
  return (
    <Pressable
      onPress={onOpen}
      style={({ pressed }) => [
        styles.storyCard,
        { borderBottomColor: colors.border },
        pressed && styles.pressed,
      ]}
    >
      <View style={styles.storyNumber}>
        <Text style={[styles.storyNumberText, { color: colors.textMuted }]}>
          {String(index).padStart(2, '0')}
        </Text>
      </View>
      <View style={styles.storyBody}>
        <View style={styles.storyMeta}>
          <Text style={[styles.storySource, { color: colors.accent }]}>{article.sourceLabel}</Text>
          <Text style={[styles.storyTime, { color: colors.textMuted }]}>
            {article.publishedAt}
          </Text>
        </View>
        <Text style={[styles.storyTitle, { color: colors.text }]}>{article.title}</Text>
        {article.translation && (
          <Text style={[styles.storyTranslation, { color: colors.textMuted }]}>
            {article.translation}
          </Text>
        )}
        <Text style={[styles.storySummary, { color: colors.textMuted }]} numberOfLines={2}>
          {article.summary}
        </Text>
        <View style={styles.storyBottom}>
          <Text style={[styles.storyTag, { color: colors.textMuted }]}>
            {article.category} · {article.region}
          </Text>
          <Pressable
            accessibilityLabel={isSaved ? '取消收藏' : '收藏新闻'}
            hitSlop={10}
            onPress={(event) => {
              event.stopPropagation();
              onToggleSaved();
            }}
          >
            <Text style={[styles.saveIcon, { color: isSaved ? colors.accent : colors.textMuted }]}>
              {isSaved ? '★' : '☆'}
            </Text>
          </Pressable>
        </View>
      </View>
    </Pressable>
  );
}

function MetricBars({
  article,
  colors,
  compact,
}: {
  article: Article;
  colors: Colors;
  compact: boolean;
}) {
  return (
    <View style={[styles.metrics, compact && styles.metricsCompact]}>
      <Metric label="影响力" value={article.impact} colors={colors} />
      <Metric label="热度" value={article.heat} colors={colors} />
    </View>
  );
}

function Metric({ label, value, colors }: { label: string; value: number; colors: Colors }) {
  return (
    <View style={styles.metric}>
      <View style={styles.metricLabelRow}>
        <Text style={[styles.metricLabel, { color: colors.textMuted }]}>{label}</Text>
        <Text style={[styles.metricValue, { color: colors.text }]}>{value}</Text>
      </View>
      <View style={[styles.metricTrack, { backgroundColor: colors.surfaceMuted }]}>
        <View style={[styles.metricFill, { backgroundColor: colors.accent, width: `${value}%` }]} />
      </View>
    </View>
  );
}

function CardActions({
  article,
  colors,
  isSaved,
  onShare,
  onToggleSaved,
}: {
  article: Article;
  colors: Colors;
  isSaved: boolean;
  onShare: () => void;
  onToggleSaved: () => void;
}) {
  return (
    <View style={[styles.cardActions, { borderTopColor: colors.border }]}>
      <Text style={[styles.actionSource, { color: colors.textMuted }]}>{article.source}</Text>
      <View style={styles.actionButtons}>
        <Pressable accessibilityLabel="分享新闻" hitSlop={10} onPress={onShare}>
          <Text style={[styles.actionText, { color: colors.textMuted }]}>分享</Text>
        </Pressable>
        <Pressable accessibilityLabel={isSaved ? '取消收藏' : '收藏新闻'} hitSlop={10} onPress={onToggleSaved}>
          <Text style={[styles.actionText, { color: isSaved ? colors.accent : colors.textMuted }]}>
            {isSaved ? '已收藏' : '收藏'}
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

function ArticleDetail({
  article,
  colors,
  isDark,
  isSaved,
  onBack,
  onShare,
  onToggleSaved,
}: {
  article: Article;
  colors: Colors;
  isDark: boolean;
  isSaved: boolean;
  onBack: () => void;
  onShare: () => void;
  onToggleSaved: () => void;
}) {
  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: colors.background }]}>
      <StatusBar style={isDark ? 'light' : 'dark'} />
      <ScrollView contentContainerStyle={styles.detailContent} showsVerticalScrollIndicator={false}>
        <View style={[styles.detailTopBar, { borderBottomColor: colors.border }]}>
          <Pressable accessibilityLabel="返回日报" hitSlop={12} onPress={onBack}>
            <Text style={[styles.backText, { color: colors.text }]}>‹ 返回</Text>
          </Pressable>
          <Text style={[styles.detailTopLabel, { color: colors.textMuted }]}>新闻详情</Text>
          <Pressable accessibilityLabel={isSaved ? '取消收藏' : '收藏新闻'} hitSlop={12} onPress={onToggleSaved}>
            <Text style={[styles.detailSave, { color: isSaved ? colors.accent : colors.text }]}>
              {isSaved ? '★' : '☆'}
            </Text>
          </Pressable>
        </View>
        <Text style={[styles.detailKicker, { color: colors.accent }]}>
          {article.category} · {article.region} · {article.publishedAt}
        </Text>
        <Text style={[styles.detailTitle, { color: colors.text }]}>{article.title}</Text>
        {article.translation && (
          <Text style={[styles.detailTranslation, { color: colors.textMuted }]}>
            {article.translation}
          </Text>
        )}
        <View style={styles.detailRule} />
        <Text style={[styles.detailSummary, { color: colors.text }]}>{article.summary}</Text>
        <View style={[styles.reasonBox, { backgroundColor: colors.accentSoft }]}>
          <Text style={[styles.reasonLabel, { color: colors.accent }]}>入选理由</Text>
          <Text style={[styles.reasonText, { color: colors.text }]}>{article.reason}</Text>
        </View>
        <MetricBars article={article} colors={colors} compact />
        <View style={[styles.detailInfo, { borderTopColor: colors.border }]}>
          <Text style={[styles.detailInfoLabel, { color: colors.textMuted }]}>来源</Text>
          <Text style={[styles.detailInfoValue, { color: colors.text }]}>{article.source}</Text>
        </View>
        <View style={[styles.detailInfo, { borderTopColor: colors.border }]}>
          <Text style={[styles.detailInfoLabel, { color: colors.textMuted }]}>AI 处理</Text>
          <Text style={[styles.detailInfoValue, { color: colors.text }]}>翻译 · 摘要 · 去重 · 排序</Text>
        </View>
        <View style={styles.detailActions}>
          <Pressable
            onPress={onShare}
            style={[styles.primaryButton, { backgroundColor: colors.text }]}
          >
            <Text style={[styles.primaryButtonText, { color: colors.background }]}>分享摘要</Text>
          </Pressable>
          <Pressable
            onPress={() => Linking.openURL(article.url)}
            style={[styles.secondaryButton, { borderColor: colors.border }]}
          >
            <Text style={[styles.secondaryButtonText, { color: colors.text }]}>阅读原文 ↗</Text>
          </Pressable>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function SavedScreen({
  articles: savedArticles,
  colors,
  isDark: _isDark,
  onOpenArticle,
  onToggleSaved,
}: {
  articles: Article[];
  colors: Colors;
  isDark: boolean;
  onOpenArticle: (article: Article) => void;
  onToggleSaved: (articleId: string) => void;
}) {
  return (
    <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
      <Masthead colors={colors} eyebrow="YOUR READING LIST" />
      <Text style={[styles.sectionTitle, styles.libraryTitle, { color: colors.text }]}>收藏</Text>
      <Text style={[styles.librarySubtitle, { color: colors.textMuted }]}>
        你保存的内容会一直留在本机。
      </Text>
      {savedArticles.length === 0 ? (
        <EmptyState
          colors={colors}
          title="还没有收藏"
          body="在日报中看到值得反复阅读的内容，可以点按“收藏”。"
        />
      ) : (
        savedArticles.map((article, index) => (
          <StoryCard
            key={article.id}
            article={article}
            colors={colors}
            isRead={false}
            isSaved
            index={index + 1}
            onOpen={() => onOpenArticle(article)}
            onToggleSaved={() => onToggleSaved(article.id)}
          />
        ))
      )}
    </ScrollView>
  );
}

function HistoryScreen({
  articles: historyArticles,
  colors,
  isDark: _isDark,
  onOpenArticle,
}: {
  articles: Article[];
  colors: Colors;
  isDark: boolean;
  onOpenArticle: (article: Article) => void;
}) {
  return (
    <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
      <Masthead colors={colors} eyebrow="ARCHIVE · 30 DAYS" />
      <Text style={[styles.sectionTitle, styles.libraryTitle, { color: colors.text }]}>历史日报</Text>
      <Text style={[styles.librarySubtitle, { color: colors.textMuted }]}>
        收藏内容不会被清理，未收藏日报保留最近 30 天。
      </Text>
      <Pressable
        disabled={historyArticles.length === 0}
        onPress={() => historyArticles[0] && onOpenArticle(historyArticles[0])}
        style={({ pressed }) => [
          styles.digestRow,
          { backgroundColor: colors.surface, borderColor: colors.border },
          pressed && styles.pressed,
        ]}
      >
        <View>
          <Text style={[styles.digestDate, { color: colors.text }]}>今天 · 07:30</Text>
          <Text style={[styles.digestMeta, { color: colors.textMuted }]}>
            {historyArticles.length} 条内容 · AI 与科技
          </Text>
        </View>
        <Text style={[styles.digestArrow, { color: colors.accent }]}>›</Text>
      </Pressable>
      <View style={[styles.historyHint, { backgroundColor: colors.surfaceMuted }]}>
        <Text style={[styles.historyHintTitle, { color: colors.text }]}>历史归档规则</Text>
        <Text style={[styles.historyHintBody, { color: colors.textMuted }]}>
          日报会在本机保存。超过 30 天且没有收藏的内容会自动清理，以保持应用轻量。
        </Text>
      </View>
    </ScrollView>
  );
}

function SettingsScreen({
  colors,
  isDark,
  mode,
  notificationsEnabled,
  apiKeyConfigured,
  apiOperation,
  apiMessage,
  onChangeMode,
  onChangeNotifications,
  onSaveApiKey,
  onDeleteApiKey,
  onTestApiKey,
}: {
  colors: Colors;
  isDark: boolean;
  mode: ThemeMode;
  notificationsEnabled: boolean;
  apiKeyConfigured: boolean;
  apiOperation: ApiOperationState;
  apiMessage: string;
  onChangeMode: (mode: ThemeMode) => void;
  onChangeNotifications: (enabled: boolean) => void;
  onSaveApiKey: (apiKey: string) => Promise<boolean>;
  onDeleteApiKey: () => Promise<void>;
  onTestApiKey: () => Promise<void>;
}) {
  const [apiKeyDraft, setApiKeyDraft] = useState('');
  const [apiKeyVisible, setApiKeyVisible] = useState(false);
  const apiBusy = apiOperation === 'saving' || apiOperation === 'testing';

  const saveKey = async () => {
    const saved = await onSaveApiKey(apiKeyDraft);
    if (saved) {
      setApiKeyDraft('');
      setApiKeyVisible(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
      <Masthead colors={colors} eyebrow="PREFERENCES" />
      <Text style={[styles.sectionTitle, styles.libraryTitle, { color: colors.text }]}>设置</Text>
      <Text style={[styles.librarySubtitle, { color: colors.textMuted }]}>
        简洁、专业，只保留真正有用的选项。
      </Text>

      <SettingsGroup label="外观" colors={colors}>
        <View style={styles.modeRow}>
          {(['system', 'light', 'dark'] as ThemeMode[]).map((option) => (
            <Pressable
              key={option}
              accessibilityRole="button"
              onPress={() => onChangeMode(option)}
              style={[
                styles.modeOption,
                { borderColor: colors.border },
                mode === option && { backgroundColor: colors.text, borderColor: colors.text },
              ]}
            >
              <Text
                style={[
                  styles.modeOptionText,
                  { color: mode === option ? colors.background : colors.textMuted },
                ]}
              >
                {option === 'system' ? '跟随系统' : option === 'light' ? '浅色' : '深色'}
              </Text>
            </Pressable>
          ))}
        </View>
        <SettingsRow
          label="当前模式"
          value={isDark ? '深色' : '浅色'}
          colors={colors}
          hideDivider
        />
      </SettingsGroup>

      <SettingsGroup label="AI 服务" colors={colors}>
        <View style={[styles.apiStatusRow, { borderBottomColor: colors.border }]}>
          <View style={styles.apiStatusText}>
            <View style={styles.apiStatusTitleRow}>
              <View
                style={[
                  styles.apiStatusDot,
                  { backgroundColor: apiKeyConfigured ? '#3B8D62' : colors.textMuted },
                ]}
              />
              <Text style={[styles.settingsLabel, { color: colors.text }]}>
                DeepSeek V4 Pro
              </Text>
            </View>
            <Text
              style={[
                styles.settingsDescription,
                { color: apiOperation === 'error' ? colors.accent : colors.textMuted },
              ]}
            >
              {apiMessage}
            </Text>
          </View>
          {apiBusy && <ActivityIndicator color={colors.accent} size="small" />}
        </View>

        <View style={styles.apiForm}>
          <Text style={[styles.apiHelp, { color: colors.textMuted }]}>
            Key 只保存在当前 iPhone 钥匙串，不会上传到 GitHub。
          </Text>
          <View style={[styles.apiInputWrap, { borderColor: colors.border }]}>
            <TextInput
              autoCapitalize="none"
              autoCorrect={false}
              editable={!apiBusy}
              onChangeText={setApiKeyDraft}
              placeholder={apiKeyConfigured ? '输入新 Key 可覆盖当前配置' : '输入 sk- 开头的 API Key'}
              placeholderTextColor={colors.textMuted}
              secureTextEntry={!apiKeyVisible}
              selectionColor={colors.accent}
              style={[styles.apiInput, { color: colors.text }]}
              value={apiKeyDraft}
            />
            <Pressable
              accessibilityLabel={apiKeyVisible ? '隐藏 API Key' : '显示 API Key'}
              disabled={!apiKeyDraft}
              hitSlop={8}
              onPress={() => setApiKeyVisible((visible) => !visible)}
            >
              <Text
                style={[
                  styles.apiVisibility,
                  { color: apiKeyDraft ? colors.accent : colors.textMuted },
                ]}
              >
                {apiKeyVisible ? '隐藏' : '显示'}
              </Text>
            </Pressable>
          </View>

          <View style={styles.apiButtonRow}>
            <Pressable
              disabled={apiBusy || !apiKeyDraft.trim()}
              onPress={saveKey}
              style={[
                styles.apiPrimaryButton,
                { backgroundColor: colors.text },
                (apiBusy || !apiKeyDraft.trim()) && styles.disabledButton,
              ]}
            >
              <Text style={[styles.apiPrimaryButtonText, { color: colors.background }]}>
                保存 Key
              </Text>
            </Pressable>
            <Pressable
              disabled={apiBusy || !apiKeyConfigured}
              onPress={onTestApiKey}
              style={[
                styles.apiSecondaryButton,
                { borderColor: colors.border },
                (apiBusy || !apiKeyConfigured) && styles.disabledButton,
              ]}
            >
              <Text style={[styles.apiSecondaryButtonText, { color: colors.text }]}>
                测试连接
              </Text>
            </Pressable>
          </View>

          {apiKeyConfigured && (
            <Pressable disabled={apiBusy} hitSlop={8} onPress={onDeleteApiKey}>
              <Text style={[styles.apiDeleteText, { color: colors.accent }]}>
                删除本机 API Key
              </Text>
            </Pressable>
          )}
          <Text style={[styles.apiCostNote, { color: colors.textMuted }]}>
            “测试连接”会向 DeepSeek 发送一个极短请求，并产生少量用量。
          </Text>
        </View>
      </SettingsGroup>

      <SettingsGroup label="提醒" colors={colors}>
        <View style={styles.settingsRow}>
          <View>
            <Text style={[styles.settingsLabel, { color: colors.text }]}>每日日报</Text>
            <Text style={[styles.settingsDescription, { color: colors.textMuted }]}>
              北京时间 07:30
            </Text>
          </View>
          <Switch
            value={notificationsEnabled}
            onValueChange={onChangeNotifications}
            trackColor={{ false: colors.surfaceMuted, true: colors.accent }}
            thumbColor={colors.surface}
          />
        </View>
        <SettingsRow
          label="抓取频率"
          value="每 2 小时"
          colors={colors}
          hideDivider
        />
      </SettingsGroup>

      <SettingsGroup label="关于" colors={colors}>
        <SettingsRow label="版本" value="0.1.0 · 个人测试版" colors={colors} />
        <SettingsRow label="内容范围" value="AI 与科技" colors={colors} />
        <SettingsRow label="数据位置" value="本机存储" colors={colors} hideDivider />
      </SettingsGroup>

      <View style={[styles.settingsFootnote, { borderTopColor: colors.border }]}>
        <Text style={[styles.settingsFootnoteText, { color: colors.textMuted }]}>
          Guo 的日报 · 为自己保留一份清醒的每日简报
        </Text>
      </View>
    </ScrollView>
  );
}

function SettingsGroup({
  label,
  colors,
  children,
}: {
  label: string;
  colors: Colors;
  children: React.ReactNode;
}) {
  return (
    <View style={styles.settingsGroup}>
      <Text style={[styles.settingsGroupLabel, { color: colors.textMuted }]}>{label}</Text>
      <View style={[styles.settingsCard, { backgroundColor: colors.surface, borderColor: colors.border }]}>
        {children}
      </View>
    </View>
  );
}

function SettingsRow({
  label,
  value,
  colors,
  hideDivider = false,
}: {
  label: string;
  value: string;
  colors: Colors;
  hideDivider?: boolean;
}) {
  return (
    <View style={[styles.settingsRow, !hideDivider && { borderBottomColor: colors.border, borderBottomWidth: 1 }]}>
      <Text style={[styles.settingsLabel, { color: colors.text }]}>{label}</Text>
      <Text style={[styles.settingsValue, { color: colors.textMuted }]}>{value}</Text>
    </View>
  );
}

function EmptyState({ colors, title, body }: { colors: Colors; title: string; body: string }) {
  return (
    <View style={[styles.emptyState, { borderColor: colors.border }]}>
      <Text style={[styles.emptyMark, { color: colors.accent }]}>—</Text>
      <Text style={[styles.emptyTitle, { color: colors.text }]}>{title}</Text>
      <Text style={[styles.emptyBody, { color: colors.textMuted }]}>{body}</Text>
    </View>
  );
}

function BottomTabs({
  activeTab,
  colors,
  onChange,
}: {
  activeTab: Tab;
  colors: Colors;
  onChange: (tab: Tab) => void;
}) {
  const tabs: { id: Tab; label: string; icon: string }[] = [
    { id: 'today', label: '今日', icon: '◉' },
    { id: 'saved', label: '收藏', icon: '☆' },
    { id: 'history', label: '历史', icon: '◷' },
    { id: 'settings', label: '设置', icon: '≡' },
  ];

  return (
    <View style={[styles.bottomTabs, { backgroundColor: colors.surface, borderTopColor: colors.border }]}>
      {tabs.map((tab) => {
        const active = activeTab === tab.id;
        return (
          <Pressable
            key={tab.id}
            accessibilityLabel={`打开${tab.label}`}
            accessibilityRole="tab"
            onPress={() => onChange(tab.id)}
            style={({ pressed }) => [styles.tab, pressed && styles.pressed]}
          >
            <Text style={[styles.tabIcon, { color: active ? colors.accent : colors.tab }]}>
              {tab.icon}
            </Text>
            <Text style={[styles.tabLabel, { color: active ? colors.text : colors.tab }]}>
              {tab.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1 },
  appFrame: { flex: 1 },
  scrollContent: { paddingHorizontal: 20, paddingBottom: 28 },
  masthead: {
    alignItems: 'flex-end',
    borderBottomWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingBottom: 14,
    paddingTop: 10,
  },
  eyebrow: { fontSize: 10, fontWeight: '700', letterSpacing: 1.5, marginBottom: 4 },
  wordmark: { fontFamily: 'Georgia', fontSize: 26, fontWeight: '700', letterSpacing: -0.5 },
  mastheadDate: { alignItems: 'flex-end', gap: 6 },
  dateLabel: { fontSize: 11, fontWeight: '600' },
  liveDot: { borderRadius: 3, height: 6, width: 6 },
  sectionIntro: { alignItems: 'flex-end', flexDirection: 'row', justifyContent: 'space-between', paddingTop: 26 },
  sectionKicker: { fontSize: 10, fontWeight: '700', letterSpacing: 1.2, marginBottom: 5 },
  sectionTitle: { fontFamily: 'Georgia', fontSize: 27, fontWeight: '700', letterSpacing: -0.4 },
  refreshButton: { alignItems: 'center', flexDirection: 'row', gap: 6, padding: 8 },
  refreshText: { fontSize: 13, fontWeight: '700' },
  pressed: { opacity: 0.58 },
  editionNote: { marginTop: 16, paddingHorizontal: 12, paddingVertical: 9 },
  editionNoteText: { fontSize: 11, fontWeight: '600', letterSpacing: 0.1 },
  newsErrorBanner: { borderLeftWidth: 3, marginTop: 12, paddingHorizontal: 12, paddingVertical: 10 },
  newsErrorTitle: { fontSize: 12, fontWeight: '800' },
  newsErrorBody: { fontSize: 11, lineHeight: 17, marginTop: 3 },
  leadCard: { borderWidth: 1, marginTop: 18, padding: 16 },
  cardMetaRow: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  metaLeft: { alignItems: 'center', flexDirection: 'row', gap: 10 },
  cardKicker: { fontSize: 11, fontWeight: '800', letterSpacing: 0.7 },
  cardRegion: { fontSize: 11 },
  readLabel: { fontSize: 10, fontWeight: '600' },
  leadTitle: { fontFamily: 'Georgia', fontSize: 24, fontWeight: '700', lineHeight: 30, marginTop: 14 },
  leadTranslation: { fontSize: 14, lineHeight: 22, marginTop: 8 },
  leadSummary: { fontSize: 14, lineHeight: 22, marginTop: 14 },
  metrics: { flexDirection: 'row', gap: 16, marginTop: 18 },
  metricsCompact: { marginBottom: 8, marginTop: 24 },
  metric: { flex: 1 },
  metricLabelRow: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', marginBottom: 5 },
  metricLabel: { fontSize: 10, fontWeight: '700' },
  metricValue: { fontSize: 11, fontWeight: '800' },
  metricTrack: { height: 4, overflow: 'hidden' },
  metricFill: { height: 4 },
  cardActions: { alignItems: 'center', borderTopWidth: 1, flexDirection: 'row', justifyContent: 'space-between', marginTop: 18, paddingTop: 12 },
  actionSource: { flex: 1, fontSize: 10 },
  actionButtons: { flexDirection: 'row', gap: 18 },
  actionText: { fontSize: 11, fontWeight: '700' },
  listHeader: { alignItems: 'baseline', flexDirection: 'row', justifyContent: 'space-between', paddingTop: 28, paddingBottom: 7 },
  listHeaderText: { fontFamily: 'Georgia', fontSize: 18, fontWeight: '700' },
  listHeaderMeta: { fontSize: 11 },
  storyCard: { borderBottomWidth: 1, flexDirection: 'row', paddingVertical: 16 },
  storyNumber: { paddingRight: 12, paddingTop: 2, width: 36 },
  storyNumberText: { fontFamily: 'Georgia', fontSize: 13 },
  storyBody: { flex: 1 },
  storyMeta: { alignItems: 'center', flexDirection: 'row', gap: 8 },
  storySource: { fontSize: 10, fontWeight: '800', letterSpacing: 0.4 },
  storyTime: { fontSize: 10 },
  storyTitle: { fontFamily: 'Georgia', fontSize: 17, fontWeight: '700', lineHeight: 22, marginTop: 7 },
  storyTranslation: { fontSize: 12, lineHeight: 18, marginTop: 4 },
  storySummary: { fontSize: 12, lineHeight: 18, marginTop: 8 },
  storyBottom: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', marginTop: 9 },
  storyTag: { fontSize: 10 },
  saveIcon: { fontSize: 22, lineHeight: 22 },
  footerNote: { borderTopWidth: 1, marginTop: 22, paddingTop: 16 },
  footerNoteTitle: { fontFamily: 'Georgia', fontSize: 14, fontWeight: '700' },
  footerNoteBody: { fontSize: 11, lineHeight: 17, marginTop: 6 },
  bottomTabs: { borderTopWidth: 1, flexDirection: 'row', paddingBottom: 8, paddingTop: 8 },
  tab: { alignItems: 'center', flex: 1, gap: 2, paddingVertical: 5 },
  tabIcon: { fontSize: 18, lineHeight: 20 },
  tabLabel: { fontSize: 10, fontWeight: '700' },
  detailContent: { paddingBottom: 40, paddingHorizontal: 20 },
  detailTopBar: { alignItems: 'center', borderBottomWidth: 1, flexDirection: 'row', justifyContent: 'space-between', paddingBottom: 14, paddingTop: 10 },
  backText: { fontSize: 14, fontWeight: '700' },
  detailTopLabel: { fontSize: 11, fontWeight: '700', letterSpacing: 0.8 },
  detailSave: { fontSize: 23, lineHeight: 23 },
  detailKicker: { fontSize: 11, fontWeight: '800', letterSpacing: 0.6, marginTop: 28 },
  detailTitle: { fontFamily: 'Georgia', fontSize: 30, fontWeight: '700', lineHeight: 36, marginTop: 10 },
  detailTranslation: { fontSize: 15, lineHeight: 23, marginTop: 9 },
  detailRule: { backgroundColor: '#B23A2B', height: 3, marginTop: 22, width: 42 },
  detailSummary: { fontFamily: 'Georgia', fontSize: 17, lineHeight: 27, marginTop: 22 },
  reasonBox: { marginTop: 24, padding: 14 },
  reasonLabel: { fontSize: 10, fontWeight: '800', letterSpacing: 0.8 },
  reasonText: { fontSize: 13, lineHeight: 20, marginTop: 6 },
  detailInfo: { borderTopWidth: 1, flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 13 },
  detailInfoLabel: { fontSize: 11 },
  detailInfoValue: { flex: 1, fontSize: 11, marginLeft: 18, textAlign: 'right' },
  detailActions: { gap: 10, marginTop: 22 },
  primaryButton: { alignItems: 'center', paddingVertical: 14 },
  primaryButtonText: { fontSize: 13, fontWeight: '800' },
  secondaryButton: { alignItems: 'center', borderWidth: 1, paddingVertical: 13 },
  secondaryButtonText: { fontSize: 13, fontWeight: '700' },
  libraryTitle: { marginTop: 26 },
  librarySubtitle: { fontSize: 13, lineHeight: 20, marginTop: 7 },
  emptyState: { alignItems: 'center', borderWidth: 1, marginTop: 28, paddingHorizontal: 24, paddingVertical: 36 },
  emptyMark: { fontFamily: 'Georgia', fontSize: 30 },
  emptyTitle: { fontFamily: 'Georgia', fontSize: 19, fontWeight: '700', marginTop: 10 },
  emptyBody: { fontSize: 13, lineHeight: 20, marginTop: 7, textAlign: 'center' },
  digestRow: { alignItems: 'center', borderWidth: 1, flexDirection: 'row', justifyContent: 'space-between', marginTop: 24, padding: 16 },
  digestDate: { fontFamily: 'Georgia', fontSize: 17, fontWeight: '700' },
  digestMeta: { fontSize: 11, marginTop: 6 },
  digestArrow: { fontSize: 28, fontWeight: '300' },
  historyHint: { marginTop: 18, padding: 14 },
  historyHintTitle: { fontSize: 12, fontWeight: '800' },
  historyHintBody: { fontSize: 12, lineHeight: 18, marginTop: 6 },
  settingsGroup: { marginTop: 24 },
  settingsGroupLabel: { fontSize: 10, fontWeight: '800', letterSpacing: 1, marginBottom: 8 },
  settingsCard: { borderWidth: 1, paddingHorizontal: 14 },
  modeRow: { flexDirection: 'row', gap: 8, paddingBottom: 13, paddingTop: 13 },
  modeOption: { alignItems: 'center', borderWidth: 1, flex: 1, paddingVertical: 10 },
  modeOptionText: { fontSize: 11, fontWeight: '700' },
  settingsRow: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', minHeight: 50, paddingVertical: 10 },
  settingsLabel: { fontSize: 13, fontWeight: '600' },
  settingsDescription: { fontSize: 11, marginTop: 3 },
  settingsValue: { fontSize: 12 },
  apiStatusRow: {
    alignItems: 'center',
    borderBottomWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    minHeight: 60,
    paddingVertical: 11,
  },
  apiStatusText: { flex: 1, paddingRight: 12 },
  apiStatusTitleRow: { alignItems: 'center', flexDirection: 'row', gap: 8 },
  apiStatusDot: { borderRadius: 4, height: 8, width: 8 },
  apiForm: { paddingBottom: 15, paddingTop: 13 },
  apiHelp: { fontSize: 11, lineHeight: 17 },
  apiInputWrap: {
    alignItems: 'center',
    borderWidth: 1,
    flexDirection: 'row',
    marginTop: 11,
    minHeight: 48,
    paddingHorizontal: 12,
  },
  apiInput: { flex: 1, fontSize: 13, paddingRight: 10, paddingVertical: 11 },
  apiVisibility: { fontSize: 11, fontWeight: '800' },
  apiButtonRow: { flexDirection: 'row', gap: 9, marginTop: 10 },
  apiPrimaryButton: { alignItems: 'center', flex: 1, paddingVertical: 12 },
  apiPrimaryButtonText: { fontSize: 12, fontWeight: '800' },
  apiSecondaryButton: { alignItems: 'center', borderWidth: 1, flex: 1, paddingVertical: 11 },
  apiSecondaryButtonText: { fontSize: 12, fontWeight: '800' },
  disabledButton: { opacity: 0.4 },
  apiDeleteText: { fontSize: 11, fontWeight: '700', marginTop: 13, textAlign: 'center' },
  apiCostNote: { fontSize: 10, lineHeight: 15, marginTop: 10 },
  settingsFootnote: { borderTopWidth: 1, marginTop: 30, paddingTop: 14 },
  settingsFootnoteText: { fontFamily: 'Georgia', fontSize: 12, textAlign: 'center' },
});

export default App;
