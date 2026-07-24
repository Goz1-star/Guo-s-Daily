package com.guo.daily;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DeepSeekClient {
    static List<NewsItem> rankAndEnhance(List<NewsItem> candidates, String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) return new ArrayList<>();

        JSONArray inputItems = new JSONArray();
        int limit = Math.min(candidates.size(), 28);
        for (int i = 0; i < limit; i++) {
            NewsItem item = candidates.get(i);
            JSONObject value = new JSONObject();
            value.put("id", item.id);
            value.put("title_en", item.titleEn);
            value.put("title_zh", item.titleZh);
            value.put("summary", item.summary);
            value.put("source", item.source);
            value.put("source_count", item.sourceCount);
            value.put("local_score", item.score);
            inputItems.put(value);
        }

        String system = "你是严谨的科技新闻编辑。只依据输入内容工作，不得补造事实。"
                + "从候选新闻中去重、合并并选出影响力和热度最高的最多10条，只保留AI与科技新闻。"
                + "英文标题保持原文并给出准确的中文小标题；中文标题不要改写。"
                + "摘要必须简短、可核验；证据不足时明确写“信息仍待更多来源确认”。"
                + "输出JSON对象，格式为：{\"items\":[{\"id\":\"原始id\",\"title_zh\":\"\","
                + "\"summary\":\"\",\"reason\":\"一句入选理由\",\"score\":0到100,\"region\":\"国内或国际\"}]}。"
                + "不得输出输入中不存在的id。";

        JSONObject body = new JSONObject();
        body.put("model", "deepseek-v4-pro");
        body.put("temperature", 0.1);
        body.put("response_format", new JSONObject().put("type", "json_object"));
        body.put("messages", new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", system))
                .put(new JSONObject().put("role", "user")
                        .put("content", new JSONObject().put("candidates", inputItems).toString())));

        URL url = new URL("https://api.deepseek.com/chat/completions");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(45000);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
            throw new IllegalStateException("DeepSeek HTTP " + connection.getResponseCode());
        }
        String response = read(connection);
        JSONObject responseJson = new JSONObject(response);
        String content = responseJson.getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").getString("content").trim();
        if (content.startsWith("```")) {
            content = content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        JSONArray ranked = new JSONObject(content).optJSONArray("items");
        if (ranked == null) return new ArrayList<>();

        Map<String, NewsItem> byId = new HashMap<>();
        for (NewsItem item : candidates) byId.put(item.id, item);
        List<NewsItem> result = new ArrayList<>();
        for (int i = 0; i < ranked.length() && result.size() < 10; i++) {
            JSONObject enriched = ranked.optJSONObject(i);
            if (enriched == null) continue;
            NewsItem item = byId.get(enriched.optString("id"));
            if (item == null) continue;
            String titleZh = enriched.optString("title_zh").trim();
            String summary = enriched.optString("summary").trim();
            String reason = enriched.optString("reason").trim();
            if (!titleZh.isEmpty()) item.titleZh = titleZh;
            if (!summary.isEmpty()) item.summary = summary;
            if (!reason.isEmpty()) item.reason = reason;
            item.score = Math.max(0, Math.min(100, enriched.optInt("score", item.score)));
            String region = enriched.optString("region");
            if ("国内".equals(region) || "国际".equals(region)) item.region = region;
            result.add(item);
        }
        return result;
    }

    private static String read(HttpURLConnection connection) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private DeepSeekClient() {}
}
