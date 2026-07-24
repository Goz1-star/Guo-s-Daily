package com.guo.daily;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class LocalStore {
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("guo_daily_data", Context.MODE_PRIVATE);
    }

    static void saveToday(Context context, List<NewsItem> items) {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        String json = encode(items);
        SharedPreferences preferences = prefs(context);
        JSONArray dates = safeArray(preferences.getString("history_dates", "[]"));
        boolean exists = false;
        for (int i = 0; i < dates.length(); i++) {
            if (date.equals(dates.optString(i))) exists = true;
        }
        if (!exists) dates.put(date);
        preferences.edit()
                .putString("today", json)
                .putString("daily_" + date, json)
                .putString("history_dates", dates.toString())
                .putLong("last_updated", System.currentTimeMillis())
                .apply();
        pruneHistory(context);
    }

    static List<NewsItem> getToday(Context context) {
        return decode(prefs(context).getString("today", "[]"));
    }

    static List<NewsItem> getDaily(Context context, String date) {
        return decode(prefs(context).getString("daily_" + date, "[]"));
    }

    static List<String> historyDates(Context context) {
        JSONArray array = safeArray(prefs(context).getString("history_dates", "[]"));
        List<String> dates = new ArrayList<>();
        for (int i = array.length() - 1; i >= 0; i--) {
            String date = array.optString(i);
            if (!date.isEmpty()) dates.add(date);
        }
        return dates;
    }

    static long lastUpdated(Context context) {
        return prefs(context).getLong("last_updated", 0L);
    }

    static boolean toggleFavorite(Context context, NewsItem item) {
        SharedPreferences preferences = prefs(context);
        JSONObject object = safeObject(preferences.getString("favorites", "{}"));
        try {
            if (object.has(item.id)) object.remove(item.id);
            else object.put(item.id, item.toJson());
        } catch (JSONException ignored) {}
        preferences.edit().putString("favorites", object.toString()).apply();
        return object.has(item.id);
    }

    static boolean isFavorite(Context context, String id) {
        return safeObject(prefs(context).getString("favorites", "{}")).has(id);
    }

    static List<NewsItem> favorites(Context context) {
        JSONObject object = safeObject(prefs(context).getString("favorites", "{}"));
        List<NewsItem> items = new ArrayList<>();
        JSONArray names = object.names();
        if (names == null) return items;
        for (int i = 0; i < names.length(); i++) {
            JSONObject item = object.optJSONObject(names.optString(i));
            if (item != null) items.add(NewsItem.fromJson(item));
        }
        return items;
    }

    static void markRead(Context context, String id) {
        Set<String> ids = new HashSet<>(prefs(context).getStringSet("read_ids", new HashSet<>()));
        ids.add(id);
        prefs(context).edit().putStringSet("read_ids", ids).apply();
    }

    static boolean isRead(Context context, String id) {
        return prefs(context).getStringSet("read_ids", new HashSet<>()).contains(id);
    }

    static void markNotInterested(Context context, String id) {
        Set<String> ids = new HashSet<>(prefs(context).getStringSet("hidden_ids", new HashSet<>()));
        ids.add(id);
        prefs(context).edit().putStringSet("hidden_ids", ids).apply();
    }

    static boolean isHidden(Context context, String id) {
        return prefs(context).getStringSet("hidden_ids", new HashSet<>()).contains(id);
    }

    private static String encode(List<NewsItem> items) {
        JSONArray array = new JSONArray();
        for (NewsItem item : items) {
            try { array.put(item.toJson()); } catch (JSONException ignored) {}
        }
        return array.toString();
    }

    private static List<NewsItem> decode(String value) {
        JSONArray array = safeArray(value == null ? "[]" : value);
        List<NewsItem> items = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null) items.add(NewsItem.fromJson(object));
        }
        return items;
    }

    private static void pruneHistory(Context context) {
        SharedPreferences preferences = prefs(context);
        JSONArray oldDates = safeArray(preferences.getString("history_dates", "[]"));
        JSONArray keep = new JSONArray();
        long cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < oldDates.length(); i++) {
            String date = oldDates.optString(i);
            try {
                Date parsed = format.parse(date);
                if (parsed != null && parsed.getTime() >= cutoff) keep.put(date);
                else editor.remove("daily_" + date);
            } catch (Exception ignored) {
                keep.put(date);
            }
        }
        editor.putString("history_dates", keep.toString()).apply();
    }

    private static JSONArray safeArray(String value) {
        try { return new JSONArray(value); } catch (JSONException error) { return new JSONArray(); }
    }

    private static JSONObject safeObject(String value) {
        try { return new JSONObject(value); } catch (JSONException error) { return new JSONObject(); }
    }

    private LocalStore() {}
}
