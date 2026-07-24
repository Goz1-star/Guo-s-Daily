package com.guo.daily;

import android.content.Context;
import android.text.Html;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class NewsRepository {
    private static final String LEARN_PROMPT =
            "https://news.learnprompt.pro/data/daily-brief.json";
    private static final String AGENTS_RADAR =
            "https://duanyytop.github.io/agents-radar/feed.xml";
    private static final String NYT_TECH =
            "https://rss.nytimes.com/services/xml/rss/nyt/Technology.xml";
    private static final Pattern AI_TERMS = Pattern.compile(
            "(?i)(\\bAI\\b|artificial intelligence|machine learning|LLM|OpenAI|Anthropic|"
                    + "DeepSeek|Gemini|Claude|robot|chip|semiconductor|人工智能|大模型|机器人|芯片)");

    interface Callback {
        void onSuccess(List<NewsItem> items, boolean aiEnhanced);
        void onError(String message, List<NewsItem> fallback);
    }

    static void refresh(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                List<NewsItem> candidates = new ArrayList<>();
                candidates.addAll(fetchLearnPrompt());
                try { candidates.addAll(fetchRss(AGENTS_RADAR, "agents-radar", 78, false)); }
                catch (Exception ignored) {}
                try { candidates.addAll(fetchRss(NYT_TECH, "The New York Times", 82, true)); }
                catch (Exception ignored) {}
                List<NewsItem> cleaned = cleanAndRank(candidates, 28);
                if (cleaned.isEmpty()) throw new IllegalStateException("新闻源暂时没有返回内容");

                String apiKey = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .getString("deepseek_key", "");
                List<NewsItem> finalItems = new ArrayList<>();
                boolean aiEnhanced = false;
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    try {
                        finalItems = DeepSeekClient.rankAndEnhance(cleaned, apiKey);
                        aiEnhanced = !finalItems.isEmpty();
                    } catch (Exception ignored) {
                        aiEnhanced = false;
                    }
                }
                if (finalItems.isEmpty()) {
                    finalItems.addAll(cleaned.subList(0, Math.min(10, cleaned.size())));
                }
                while (finalItems.size() < 10) {
                    NewsItem brief = nextUnused(cleaned, finalItems);
                    if (brief == null) break;
                    finalItems.add(brief);
                }
                LocalStore.saveToday(app, finalItems);
                callback.onSuccess(finalItems, aiEnhanced);
            } catch (Exception error) {
                List<NewsItem> fallback = LocalStore.getToday(app);
                if (fallback.isEmpty()) fallback = sampleItems();
                callback.onError(error.getMessage() == null ? "网络连接失败" : error.getMessage(), fallback);
            }
        }, "guo-news-refresh").start();
    }

    static List<NewsItem> current(Context context) {
        List<NewsItem> items = LocalStore.getToday(context);
        return items.isEmpty() ? sampleItems() : items;
    }

    private static List<NewsItem> fetchLearnPrompt() throws Exception {
        JSONObject root = new JSONObject(getText(LEARN_PROMPT));
        JSONArray array = root.optJSONArray("items");
        List<NewsItem> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject story = array.optJSONObject(i);
            if (story == null) continue;
            JSONObject primary = story.optJSONObject("primary_item");
            if (primary == null) {
                JSONArray sources = story.optJSONArray("sources");
                if (sources != null) primary = sources.optJSONObject(0);
            }
            if (primary == null) primary = story;
            String title = story.optString("title", primary.optString("title"));
            String titleEn = primary.optString("title_en");
            String titleZh = primary.optString("title_zh", title);
            if (titleEn.isEmpty() && isMostlyAscii(title)) {
                titleEn = title;
                if (titleZh.equals(title)) titleZh = "";
            }
            double importance = story.optDouble("importance",
                    story.optDouble("score", 0.72));
            String url = story.optString("primary_url",
                    primary.optString("url", story.optString("url")));
            String reason = primary.optString("recommend_reason_zh",
                    story.optString("persona_review"));
            if (reason.isEmpty()) reason = reasonFromSignals(story.optJSONArray("reasons"));
            result.add(new NewsItem(
                    story.optString("story_id", sha1(title + url)),
                    titleEn,
                    titleZh.isEmpty() ? title : titleZh,
                    primary.optString("summary"),
                    reason,
                    story.optString("source_name", primary.optString("source_name",
                            story.optString("source", "AI News Radar"))),
                    story.optString("latest_at", primary.optString("published_at")),
                    url,
                    classifyRegion(url, story.optString("source")),
                    (int) Math.round(Math.min(1.0, importance) * 100),
                    Math.max(1, story.optInt("source_count", 1))));
        }
        return result;
    }

    private static List<NewsItem> fetchRss(String address, String sourceName,
                                           int baseScore, boolean aiOnly) throws Exception {
        HttpURLConnection connection = open(address);
        List<NewsItem> result = new ArrayList<>();
        try (InputStream input = connection.getInputStream()) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, "UTF-8");
            String title = "", link = "", description = "", date = "";
            boolean inItem = false;
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("item".equalsIgnoreCase(name) || "entry".equalsIgnoreCase(name)) {
                        inItem = true;
                        title = link = description = date = "";
                    } else if (inItem && "title".equalsIgnoreCase(name)) {
                        title = safeNextText(parser);
                    } else if (inItem && ("link".equalsIgnoreCase(name))) {
                        String href = parser.getAttributeValue(null, "href");
                        link = href == null ? safeNextText(parser) : href;
                    } else if (inItem && ("description".equalsIgnoreCase(name)
                            || "summary".equalsIgnoreCase(name))) {
                        description = stripHtml(safeNextText(parser));
                    } else if (inItem && ("pubDate".equalsIgnoreCase(name)
                            || "published".equalsIgnoreCase(name)
                            || "updated".equalsIgnoreCase(name))) {
                        date = safeNextText(parser);
                    }
                } else if (event == XmlPullParser.END_TAG
                        && ("item".equalsIgnoreCase(parser.getName())
                        || "entry".equalsIgnoreCase(parser.getName()))) {
                    inItem = false;
                    String searchable = title + " " + description;
                    if (!title.isEmpty() && (!aiOnly || AI_TERMS.matcher(searchable).find())) {
                        String titleEn = isMostlyAscii(title) ? title : "";
                        String titleZh = titleEn.isEmpty() ? title : "";
                        result.add(new NewsItem(
                                sha1(title + link), titleEn, titleZh,
                                trim(description, 300),
                                sourceName.equals("The New York Times")
                                        ? "来自国际主流媒体的 AI 与科技报道。"
                                        : "来自开源 AI 生态日报的趋势信号。",
                                sourceName, date, link,
                                "国际", baseScore, 1));
                    }
                    if (result.size() >= 15) break;
                }
            }
        } finally {
            connection.disconnect();
        }
        return result;
    }

    private static List<NewsItem> cleanAndRank(List<NewsItem> input, int limit) {
        Map<String, NewsItem> unique = new LinkedHashMap<>();
        for (NewsItem item : input) {
            if (item.url.isEmpty() || item.primaryTitle().isEmpty()) continue;
            String key = normalize(item.titleEn.isEmpty() ? item.titleZh : item.titleEn);
            if (key.length() < 5) key = item.url;
            NewsItem existing = unique.get(key);
            if (existing == null || item.score > existing.score) unique.put(key, item);
            else existing.sourceCount += item.sourceCount;
        }
        List<NewsItem> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparingInt((NewsItem item) -> item.score)
                .thenComparingInt(item -> item.sourceCount).reversed());
        return new ArrayList<>(result.subList(0, Math.min(limit, result.size())));
    }

    private static NewsItem nextUnused(List<NewsItem> pool, List<NewsItem> used) {
        for (NewsItem candidate : pool) {
            boolean exists = false;
            for (NewsItem value : used) if (value.id.equals(candidate.id)) exists = true;
            if (!exists) return candidate;
        }
        return null;
    }

    private static String getText(String address) throws Exception {
        HttpURLConnection connection = open(address);
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        } finally {
            connection.disconnect();
        }
        return result.toString();
    }

    private static HttpURLConnection open(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "GuoDaily/0.1 personal-news-reader");
        connection.setRequestProperty("Accept", "application/json, application/rss+xml, application/xml, text/xml");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("新闻源 HTTP " + code);
        return connection;
    }

    private static String safeNextText(XmlPullParser parser) {
        try { return parser.nextText(); } catch (Exception ignored) { return ""; }
    }

    private static String stripHtml(String value) {
        return Html.fromHtml(value == null ? "" : value, Html.FROM_HTML_MODE_LEGACY)
                .toString().replaceAll("\\s+", " ").trim();
    }

    private static String reasonFromSignals(JSONArray reasons) {
        if (reasons == null) return "在今日 AI 与科技信息流中具有较高影响力。";
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < reasons.length(); i++) {
            String value = reasons.optString(i);
            if ("multi_source".equals(value)) labels.add("多来源共同报道");
            if ("high_importance".equals(value)) labels.add("影响力评分较高");
            if ("high_ai_relevance".equals(value)) labels.add("与 AI 高度相关");
        }
        return labels.isEmpty() ? "在今日 AI 与科技信息流中具有较高影响力。"
                : String.join("，", labels) + "。";
    }

    private static String classifyRegion(String url, String source) {
        String value = (url + " " + source).toLowerCase(Locale.ROOT);
        if (value.contains(".cn") || value.contains("36kr") || value.contains("机器之心")
                || value.contains("量子位") || value.contains("腾讯") || value.contains("新浪")) {
            return "国内";
        }
        return "国际";
    }

    private static boolean isMostlyAscii(String value) {
        if (value == null || value.isEmpty()) return false;
        int ascii = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < 128) ascii++;
        return ascii >= value.length() * 0.78;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "");
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static String sha1(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) builder.append(String.format(Locale.ROOT, "%02x", b));
            return builder.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static List<NewsItem> sampleItems() {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date());
        List<NewsItem> items = new ArrayList<>();
        items.add(new NewsItem("sample-1", "Your daily AI briefing is being prepared",
                "你的 AI 科技日报正在准备中",
                "这是离线样例内容。连接网络后，应用会从已配置的公开新闻源获取最新报道。",
                "用于展示日报的标题、摘要、来源、评分和交互层级。",
                "Guo 的日报", now, "https://news.learnprompt.pro/",
                "国际", 92, 3));
        items.add(new NewsItem("sample-2", "",
                "每两小时更新一次新闻候选池",
                "应用会在后台更新候选新闻，并在每天早上 7:30 生成不超过十条的个人日报。",
                "减少信息过载，同时避免错过重要 AI 与科技进展。",
                "系统说明", now, "https://github.com/duanyytop/agents-radar",
                "国内", 84, 2));
        return items;
    }

    private NewsRepository() {}
}
