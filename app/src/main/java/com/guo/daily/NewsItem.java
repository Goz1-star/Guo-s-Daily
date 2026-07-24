package com.guo.daily;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public final class NewsItem implements Serializable {
    public String id;
    public String titleEn;
    public String titleZh;
    public String summary;
    public String reason;
    public String source;
    public String publishedAt;
    public String url;
    public String region;
    public int score;
    public int sourceCount;

    public NewsItem(String id, String titleEn, String titleZh, String summary, String reason,
                    String source, String publishedAt, String url, String region,
                    int score, int sourceCount) {
        this.id = id;
        this.titleEn = titleEn == null ? "" : titleEn;
        this.titleZh = titleZh == null ? "" : titleZh;
        this.summary = summary == null ? "" : summary;
        this.reason = reason == null ? "" : reason;
        this.source = source == null ? "" : source;
        this.publishedAt = publishedAt == null ? "" : publishedAt;
        this.url = url == null ? "" : url;
        this.region = region == null ? "国际" : region;
        this.score = score;
        this.sourceCount = sourceCount;
    }

    public String primaryTitle() {
        return titleEn.isEmpty() ? titleZh : titleEn;
    }

    public String translationTitle() {
        return titleEn.isEmpty() || titleZh.equals(titleEn) ? "" : titleZh;
    }

    JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("titleEn", titleEn);
        object.put("titleZh", titleZh);
        object.put("summary", summary);
        object.put("reason", reason);
        object.put("source", source);
        object.put("publishedAt", publishedAt);
        object.put("url", url);
        object.put("region", region);
        object.put("score", score);
        object.put("sourceCount", sourceCount);
        return object;
    }

    static NewsItem fromJson(JSONObject object) {
        return new NewsItem(
                object.optString("id"),
                object.optString("titleEn"),
                object.optString("titleZh"),
                object.optString("summary"),
                object.optString("reason"),
                object.optString("source"),
                object.optString("publishedAt"),
                object.optString("url"),
                object.optString("region", "国际"),
                object.optInt("score", 70),
                object.optInt("sourceCount", 1));
    }
}
