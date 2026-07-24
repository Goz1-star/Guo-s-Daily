package com.guo.daily;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private ThemePalette palette;
    private FrameLayout content;
    private LinearLayout navigation;
    private String activeTab = "today";
    private boolean refreshing;
    private String lastError = "";
    private boolean lastAiEnhanced;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = new ThemePalette(this);
        ThemePalette.applySystemBars(this, palette);
        buildChrome();
        AppScheduler.scheduleAll(this);
        requestNotificationPermission();

        List<NewsItem> cached = LocalStore.getToday(this);
        if (cached.isEmpty()) {
            renderLoading();
            refreshNews();
        } else {
            renderToday(cached);
            if (System.currentTimeMillis() - LocalStore.lastUpdated(this) > 2L * 60 * 60 * 1000) {
                refreshNews();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if ("saved".equals(activeTab)) renderFavorites();
    }

    private void buildChrome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.paper);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 7),
                UiKit.dp(this, 8), UiKit.dp(this, 9));
        navigation.setBackgroundColor(palette.paper);
        navigation.setShowDividers(LinearLayout.SHOW_DIVIDER_BEGINNING);
        navigation.setDividerDrawable(UiKit.rounded(palette.line, 0, 0, this));
        root.addView(navigation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 64)));

        setContentView(root);
        buildNavigation();
    }

    private void buildNavigation() {
        navigation.removeAllViews();
        addNav("today", "今日日报", () -> renderToday(NewsRepository.current(this)));
        addNav("history", "历史", this::renderHistory);
        addNav("saved", "收藏", this::renderFavorites);
        addNav("settings", "设置", this::renderSettings);
    }

    private void addNav(String id, String label, Runnable action) {
        TextView button = UiKit.text(this, palette, label, 12,
                activeTab.equals(id) ? Typeface.BOLD : Typeface.NORMAL);
        button.setTextColor(activeTab.equals(id) ? palette.accent : palette.muted);
        button.setGravity(Gravity.CENTER);
        button.setBackground(activeTab.equals(id)
                ? UiKit.rounded(palette.accentSoft, 0, 4, this) : null);
        button.setOnClickListener(view -> {
            activeTab = id;
            buildNavigation();
            action.run();
        });
        navigation.addView(button, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private LinearLayout newPage() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(palette.paper);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 20),
                UiKit.dp(this, 20), UiKit.dp(this, 36));
        scrollView.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.removeAllViews();
        content.addView(scrollView);
        return page;
    }

    private void renderToday(List<NewsItem> allItems) {
        activeTab = "today";
        buildNavigation();
        LinearLayout page = newPage();
        addMasthead(page);

        String date = new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date());
        page.addView(UiKit.serif(this, palette, date, 30, Typeface.BOLD));
        TextView deck = UiKit.text(this, palette,
                "AI 与科技 · 今日最值得关注的不超过 10 条", 13, Typeface.NORMAL);
        deck.setTextColor(palette.muted);
        page.addView(deck);
        page.addView(UiKit.space(this, 14));
        page.addView(UiKit.divider(this, palette));
        page.addView(UiKit.space(this, 12));

        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        TextView updated = UiKit.text(this, palette, statusText(), 11, Typeface.NORMAL);
        updated.setTextColor(lastError.isEmpty() ? palette.muted : palette.error);
        status.addView(updated, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView refresh = editorialButton(refreshing ? "更新中…" : "刷新");
        refresh.setEnabled(!refreshing);
        refresh.setOnClickListener(view -> refreshNews());
        status.addView(refresh);
        page.addView(status);
        page.addView(UiKit.space(this, 16));

        List<NewsItem> items = new ArrayList<>();
        for (NewsItem item : allItems) {
            if (!LocalStore.isHidden(this, item.id)) items.add(item);
        }
        if (items.isEmpty()) {
            addStateBlock(page, "今日暂无内容",
                    "新闻源暂时没有返回符合条件的 AI 与科技新闻。你可以稍后刷新。", false);
            return;
        }

        addLeadStory(page, items.get(0));
        for (int i = 1; i < items.size(); i++) addStoryCard(page, items.get(i), i + 1);

        page.addView(UiKit.space(this, 20));
        page.addView(UiKit.divider(this, palette));
        TextView note = UiKit.text(this, palette,
                "摘要由数据源与 DeepSeek V4 Pro 辅助整理。重要判断请以原文为准。", 11,
                Typeface.NORMAL);
        note.setTextColor(palette.muted);
        note.setPadding(0, UiKit.dp(this, 12), 0, 0);
        page.addView(note);
    }

    private void addMasthead(LinearLayout page) {
        TextView brand = UiKit.serif(this, palette, "Guo 的日报", 20, Typeface.BOLD);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(0.03f);
        page.addView(brand, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView edition = UiKit.text(this, palette, "PERSONAL AI & TECHNOLOGY EDITION",
                9, Typeface.BOLD);
        edition.setTextColor(palette.muted);
        edition.setGravity(Gravity.CENTER);
        edition.setLetterSpacing(0.12f);
        page.addView(edition);
        page.addView(UiKit.space(this, 10));
        page.addView(UiKit.divider(this, palette));
        page.addView(UiKit.space(this, 18));
    }

    private void addLeadStory(LinearLayout page, NewsItem item) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, 0, 0, UiKit.dp(this, 18));
        block.setOnClickListener(view -> openDetail(item));

        LinearLayout meta = metaRow(item);
        block.addView(meta);
        block.addView(UiKit.space(this, 8));
        TextView title = UiKit.serif(this, palette, item.primaryTitle(), 29, Typeface.BOLD);
        block.addView(title);
        if (!item.translationTitle().isEmpty()) {
            TextView translation = UiKit.text(this, palette, item.translationTitle(),
                    15, Typeface.NORMAL);
            translation.setTextColor(palette.muted);
            translation.setPadding(0, UiKit.dp(this, 5), 0, 0);
            block.addView(translation);
        }
        if (!item.summary.isEmpty()) {
            TextView summary = UiKit.serif(this, palette, item.summary, 16, Typeface.NORMAL);
            summary.setTextColor(palette.muted);
            summary.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 12));
            block.addView(summary);
        }
        addScore(block, item);
        block.addView(UiKit.space(this, 12));
        block.addView(actionRow(item));
        page.addView(block);
        page.addView(UiKit.divider(this, palette));
    }

    private void addStoryCard(LinearLayout page, NewsItem item, int position) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, UiKit.dp(this, 18), 0, UiKit.dp(this, 18));
        block.setOnClickListener(view -> openDetail(item));

        TextView number = UiKit.text(this, palette,
                String.format(Locale.CHINA, "%02d", position), 11, Typeface.BOLD);
        number.setTextColor(palette.accent);
        block.addView(number);
        block.addView(UiKit.space(this, 7));
        TextView title = UiKit.serif(this, palette, item.primaryTitle(), 21, Typeface.BOLD);
        if (LocalStore.isRead(this, item.id)) title.setTextColor(palette.muted);
        block.addView(title);
        if (!item.translationTitle().isEmpty()) {
            TextView translation = UiKit.text(this, palette, item.translationTitle(),
                    13, Typeface.NORMAL);
            translation.setTextColor(palette.muted);
            translation.setPadding(0, UiKit.dp(this, 4), 0, 0);
            block.addView(translation);
        }
        if (!item.summary.isEmpty()) {
            TextView summary = UiKit.text(this, palette, item.summary, 14, Typeface.NORMAL);
            summary.setTextColor(palette.muted);
            summary.setMaxLines(3);
            summary.setPadding(0, UiKit.dp(this, 9), 0, UiKit.dp(this, 11));
            block.addView(summary);
        }
        block.addView(metaRow(item));
        block.addView(UiKit.space(this, 10));
        addScore(block, item);
        block.addView(UiKit.space(this, 10));
        block.addView(actionRow(item));
        page.addView(block);
        page.addView(UiKit.divider(this, palette));
    }

    private LinearLayout metaRow(NewsItem item) {
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(UiKit.chip(this, palette, item.region));
        TextView source = UiKit.text(this, palette,
                "  " + item.source + (item.sourceCount > 1 ? " · 多源 " + item.sourceCount : ""),
                11, Typeface.NORMAL);
        source.setTextColor(palette.muted);
        source.setMaxLines(1);
        meta.addView(source, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return meta;
    }

    private void addScore(LinearLayout parent, NewsItem item) {
        LinearLayout labelRow = new LinearLayout(this);
        TextView label = UiKit.text(this, palette, "影响力 / 热度", 10, Typeface.BOLD);
        label.setTextColor(palette.muted);
        labelRow.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView score = UiKit.text(this, palette, item.score + "", 11, Typeface.BOLD);
        score.setTextColor(palette.accent);
        labelRow.addView(score);
        parent.addView(labelRow);
        parent.addView(UiKit.space(this, 4));
        parent.addView(UiKit.scoreBar(this, palette, item.score));
    }

    private LinearLayout actionRow(NewsItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView open = smallAction("阅读全文");
        open.setOnClickListener(view -> openDetail(item));
        row.addView(open);
        TextView favorite = smallAction(LocalStore.isFavorite(this, item.id) ? "已收藏" : "收藏");
        favorite.setOnClickListener(view -> {
            boolean saved = LocalStore.toggleFavorite(this, item);
            favorite.setText(saved ? "已收藏" : "收藏");
            toast(saved ? "已收藏" : "已取消收藏");
        });
        row.addView(favorite);
        TextView share = smallAction("分享");
        share.setOnClickListener(view -> share(item));
        row.addView(share);
        TextView hide = smallAction("不感兴趣");
        hide.setTextColor(palette.muted);
        hide.setOnClickListener(view -> {
            LocalStore.markNotInterested(this, item.id);
            renderToday(NewsRepository.current(this));
        });
        row.addView(hide);
        return row;
    }

    private void renderHistory() {
        LinearLayout page = newPage();
        addPageTitle(page, "历史日报", "最近 30 天 · 已收藏内容不受清理影响");
        List<String> dates = LocalStore.historyDates(this);
        if (dates.isEmpty()) {
            addStateBlock(page, "还没有历史日报",
                    "每天生成日报后，会自动保存在这里。", false);
            return;
        }
        for (String date : dates) {
            List<NewsItem> items = LocalStore.getDaily(this, date);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, UiKit.dp(this, 18), 0, UiKit.dp(this, 18));
            TextView dateText = UiKit.serif(this, palette, date, 22, Typeface.BOLD);
            row.addView(dateText);
            TextView count = UiKit.text(this, palette, items.size() + " 条新闻", 12,
                    Typeface.NORMAL);
            count.setTextColor(palette.muted);
            row.addView(count);
            row.setOnClickListener(view -> renderHistoryDate(date, items));
            page.addView(row);
            page.addView(UiKit.divider(this, palette));
        }
    }

    private void renderHistoryDate(String date, List<NewsItem> items) {
        LinearLayout page = newPage();
        TextView back = editorialButton("← 返回历史");
        back.setOnClickListener(view -> renderHistory());
        page.addView(back);
        page.addView(UiKit.space(this, 18));
        addPageTitle(page, date, items.size() + " 条 AI 与科技新闻");
        for (int i = 0; i < items.size(); i++) addStoryCard(page, items.get(i), i + 1);
    }

    private void renderFavorites() {
        LinearLayout page = newPage();
        List<NewsItem> items = LocalStore.favorites(this);
        addPageTitle(page, "收藏", items.size() + " 条永久保存在本机");
        if (items.isEmpty()) {
            addStateBlock(page, "还没有收藏",
                    "在新闻卡片或详情页点击“收藏”，内容就会出现在这里。", false);
            return;
        }
        for (int i = 0; i < items.size(); i++) addStoryCard(page, items.get(i), i + 1);
    }

    @SuppressWarnings("deprecation")
    private void renderSettings() {
        LinearLayout page = newPage();
        addPageTitle(page, "设置", "个人设备 · 中国标准时间");
        addSectionTitle(page, "外观");
        TextView themeHint = UiKit.text(this, palette, "默认跟随系统，也可以手动覆盖。", 13,
                Typeface.NORMAL);
        themeHint.setTextColor(palette.muted);
        page.addView(themeHint);
        page.addView(UiKit.space(this, 10));
        LinearLayout themes = new LinearLayout(this);
        String current = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("theme_mode", "system");
        addThemeChoice(themes, "system", "跟随系统", current);
        addThemeChoice(themes, "light", "浅色", current);
        addThemeChoice(themes, "dark", "深色", current);
        page.addView(themes);

        addSectionTitle(page, "推送与更新");
        Switch notifications = new Switch(this);
        notifications.setText("每天 7:30 推送日报");
        notifications.setTextColor(palette.ink);
        notifications.setTextSize(15);
        notifications.setChecked(getSharedPreferences("settings", MODE_PRIVATE)
                .getBoolean("notifications", true));
        notifications.setOnCheckedChangeListener((button, checked) -> {
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putBoolean("notifications", checked).apply();
            AppScheduler.scheduleAll(this);
        });
        page.addView(notifications);
        TextView frequency = UiKit.text(this, palette,
                "候选新闻每 2 小时更新一次；日报按中国标准时间生成。", 12, Typeface.NORMAL);
        frequency.setTextColor(palette.muted);
        frequency.setPadding(0, UiKit.dp(this, 8), 0, 0);
        page.addView(frequency);
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarm != null && !alarm.canScheduleExactAlarms()) {
                TextView exactHint = UiKit.text(this, palette,
                        "系统尚未允许准时闹钟，7:30 推送可能被省电策略延迟。",
                        12, Typeface.NORMAL);
                exactHint.setTextColor(palette.error);
                exactHint.setPadding(0, UiKit.dp(this, 9), 0, UiKit.dp(this, 8));
                page.addView(exactHint);
                TextView exactPermission = editorialButton("授权准时推送");
                exactPermission.setOnClickListener(view -> {
                    Intent permission = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(permission);
                });
                page.addView(exactPermission);
            }
        }

        addSectionTitle(page, "AI 服务");
        TextView model = UiKit.text(this, palette, "DeepSeek V4 Pro · deepseek-v4-pro",
                14, Typeface.BOLD);
        page.addView(model);
        TextView keyHint = UiKit.text(this, palette,
                "API Key 仅保存在本机测试数据中。未配置时使用来源自带摘要和规则排序。",
                12, Typeface.NORMAL);
        keyHint.setTextColor(palette.muted);
        keyHint.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 10));
        page.addView(keyHint);
        EditText key = new EditText(this);
        key.setHint("粘贴 DeepSeek API Key");
        key.setHintTextColor(palette.muted);
        key.setTextColor(palette.ink);
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(getSharedPreferences("settings", MODE_PRIVATE)
                .getString("deepseek_key", ""));
        key.setBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.accent));
        page.addView(key);
        TextView saveKey = editorialButton("保存 AI 配置");
        saveKey.setOnClickListener(view -> {
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putString("deepseek_key", key.getText().toString().trim()).apply();
            toast("AI 配置已保存在本机");
        });
        page.addView(saveKey);

        addSectionTitle(page, "新闻来源");
        addSource(page, "AI News Radar", "公开 JSON · 已接入", "news.learnprompt.pro");
        addSource(page, "agents-radar", "公开 RSS · 已接入", "GitHub 开源项目");
        addSource(page, "The New York Times Technology",
                "官方 RSS · AI 关键词筛选", "更精确检索需 NYTimes API Key");

        addSectionTitle(page, "本机数据");
        TextView storage = UiKit.text(this, palette,
                "收藏永久保存。历史日报保留 30 天，过期日报会自动清理；阅读状态只用于弱化已读标题。",
                13, Typeface.NORMAL);
        storage.setTextColor(palette.muted);
        page.addView(storage);
        page.addView(UiKit.space(this, 12));
        TextView refresh = editorialButton("立即抓取并重新生成");
        refresh.setOnClickListener(view -> {
            activeTab = "today";
            buildNavigation();
            renderToday(NewsRepository.current(this));
            refreshNews();
        });
        page.addView(refresh);

        addSectionTitle(page, "关于");
        TextView about = UiKit.text(this, palette,
                "Guo 的日报 0.1.0 测试版\n个人 AI 与科技新闻阅读器", 13, Typeface.NORMAL);
        about.setTextColor(palette.muted);
        page.addView(about);
    }

    private void addThemeChoice(LinearLayout row, String id, String label, String current) {
        TextView choice = UiKit.text(this, palette, label, 12,
                id.equals(current) ? Typeface.BOLD : Typeface.NORMAL);
        choice.setTextColor(id.equals(current) ? palette.paper : palette.ink);
        choice.setGravity(Gravity.CENTER);
        choice.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 10),
                UiKit.dp(this, 8), UiKit.dp(this, 10));
        choice.setBackground(UiKit.rounded(id.equals(current) ? palette.accent : palette.surface,
                palette.line, 4, this));
        choice.setOnClickListener(view -> {
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putString("theme_mode", id).apply();
            recreate();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(0, 0, UiKit.dp(this, 6), 0);
        row.addView(choice, params);
    }

    private void addSource(LinearLayout page, String name, String status, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 12));
        row.addView(UiKit.text(this, palette, name, 14, Typeface.BOLD));
        TextView statusView = UiKit.text(this, palette, status, 12, Typeface.NORMAL);
        statusView.setTextColor(palette.accent);
        row.addView(statusView);
        TextView detailView = UiKit.text(this, palette, detail, 11, Typeface.NORMAL);
        detailView.setTextColor(palette.muted);
        row.addView(detailView);
        page.addView(row);
        page.addView(UiKit.divider(this, palette));
    }

    private void renderLoading() {
        LinearLayout page = newPage();
        addMasthead(page);
        page.addView(UiKit.space(this, 60));
        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(palette.accent));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                UiKit.dp(this, 30), UiKit.dp(this, 30));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        page.addView(progress, params);
        addStateBlock(page, "正在编排今日日报",
                "正在连接新闻源、去重并计算影响力，请稍候。", true);
    }

    private void addStateBlock(LinearLayout page, String title, String body, boolean compact) {
        if (!compact) page.addView(UiKit.space(this, 44));
        TextView titleView = UiKit.serif(this, palette, title, 24, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        page.addView(titleView);
        TextView bodyView = UiKit.text(this, palette, body, 14, Typeface.NORMAL);
        bodyView.setTextColor(palette.muted);
        bodyView.setGravity(Gravity.CENTER);
        bodyView.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 10),
                UiKit.dp(this, 16), UiKit.dp(this, 12));
        page.addView(bodyView);
        page.addView(UiKit.divider(this, palette));
    }

    private void addPageTitle(LinearLayout page, String title, String subtitle) {
        page.addView(UiKit.serif(this, palette, title, 32, Typeface.BOLD));
        TextView sub = UiKit.text(this, palette, subtitle, 13, Typeface.NORMAL);
        sub.setTextColor(palette.muted);
        sub.setPadding(0, UiKit.dp(this, 5), 0, UiKit.dp(this, 14));
        page.addView(sub);
        page.addView(UiKit.divider(this, palette));
    }

    private void addSectionTitle(LinearLayout page, String title) {
        page.addView(UiKit.space(this, 26));
        TextView heading = UiKit.text(this, palette, title.toUpperCase(Locale.CHINA),
                12, Typeface.BOLD);
        heading.setTextColor(palette.accent);
        heading.setLetterSpacing(0.08f);
        heading.setPadding(0, 0, 0, UiKit.dp(this, 10));
        page.addView(heading);
    }

    private TextView editorialButton(String label) {
        TextView button = UiKit.text(this, palette, label, 12, Typeface.BOLD);
        button.setTextColor(palette.accent);
        button.setGravity(Gravity.CENTER);
        button.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 8),
                UiKit.dp(this, 12), UiKit.dp(this, 8));
        button.setBackground(UiKit.rounded(palette.surface, palette.line, 3, this));
        return button;
    }

    private TextView smallAction(String label) {
        TextView action = UiKit.text(this, palette, label, 11, Typeface.BOLD);
        action.setTextColor(palette.accent);
        action.setPadding(0, UiKit.dp(this, 6), UiKit.dp(this, 18), UiKit.dp(this, 6));
        return action;
    }

    private void openDetail(NewsItem item) {
        LocalStore.markRead(this, item.id);
        Intent intent = new Intent(this, NewsDetailActivity.class);
        intent.putExtra("news", item);
        startActivity(intent);
    }

    private void share(NewsItem item) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        String text = item.primaryTitle()
                + (item.translationTitle().isEmpty() ? "" : "\n" + item.translationTitle())
                + "\n\n" + item.summary + "\n\n" + item.url;
        share.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, "分享新闻"));
    }

    private void refreshNews() {
        if (refreshing) return;
        refreshing = true;
        lastError = "";
        if ("today".equals(activeTab) && !LocalStore.getToday(this).isEmpty()) {
            renderToday(NewsRepository.current(this));
        }
        NewsRepository.refresh(this, new NewsRepository.Callback() {
            @Override
            public void onSuccess(List<NewsItem> items, boolean aiEnhanced) {
                runOnUiThread(() -> {
                    refreshing = false;
                    lastAiEnhanced = aiEnhanced;
                    lastError = "";
                    if ("today".equals(activeTab)) renderToday(items);
                    toast(aiEnhanced ? "日报已由 DeepSeek V4 Pro 更新" : "日报已更新");
                });
            }

            @Override
            public void onError(String message, List<NewsItem> fallback) {
                runOnUiThread(() -> {
                    refreshing = false;
                    lastError = message;
                    if ("today".equals(activeTab)) renderToday(fallback);
                });
            }
        });
    }

    private String statusText() {
        if (refreshing) return "正在更新新闻源…";
        if (!lastError.isEmpty()) return "更新失败，正在显示本地内容 · " + lastError;
        long updated = LocalStore.lastUpdated(this);
        if (updated == 0) return "离线预览 · 等待首次更新";
        String time = new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(updated));
        return time + " 更新" + (lastAiEnhanced ? " · DeepSeek 已筛选" : "");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }
}
