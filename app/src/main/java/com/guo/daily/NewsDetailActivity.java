package com.guo.daily;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class NewsDetailActivity extends Activity {
    private ThemePalette palette;
    private NewsItem item;
    private TextView favorite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = new ThemePalette(this);
        ThemePalette.applySystemBars(this, palette);
        item = (NewsItem) getIntent().getSerializableExtra("news");
        if (item == null) {
            finish();
            return;
        }
        LocalStore.markRead(this, item.id);
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.paper);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 16),
                UiKit.dp(this, 20), UiKit.dp(this, 40));
        scroll.addView(page);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = action("← 返回");
        back.setOnClickListener(view -> finish());
        top.addView(back, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        favorite = action(LocalStore.isFavorite(this, item.id) ? "已收藏" : "收藏");
        favorite.setOnClickListener(view -> {
            boolean saved = LocalStore.toggleFavorite(this, item);
            favorite.setText(saved ? "已收藏" : "收藏");
            Toast.makeText(this, saved ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
        });
        top.addView(favorite);
        TextView share = action("分享");
        share.setOnClickListener(view -> share());
        top.addView(share);
        page.addView(top);
        page.addView(UiKit.space(this, 28));

        LinearLayout chips = new LinearLayout(this);
        chips.addView(UiKit.chip(this, palette, "AI · 科技"));
        TextView region = UiKit.chip(this, palette, item.region);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipParams.setMargins(UiKit.dp(this, 7), 0, 0, 0);
        chips.addView(region, chipParams);
        page.addView(chips);
        page.addView(UiKit.space(this, 12));

        TextView headline = UiKit.serif(this, palette, item.primaryTitle(), 34, Typeface.BOLD);
        page.addView(headline);
        if (!item.translationTitle().isEmpty()) {
            TextView translation = UiKit.text(this, palette, item.translationTitle(),
                    17, Typeface.NORMAL);
            translation.setTextColor(palette.muted);
            translation.setPadding(0, UiKit.dp(this, 8), 0, 0);
            page.addView(translation);
        }

        TextView byline = UiKit.text(this, palette,
                item.source + formatMultiSource() + formatTime(), 12, Typeface.NORMAL);
        byline.setTextColor(palette.muted);
        byline.setPadding(0, UiKit.dp(this, 18), 0, UiKit.dp(this, 14));
        page.addView(byline);
        page.addView(UiKit.divider(this, palette));

        addSection(page, "为什么值得看", item.reason.isEmpty()
                ? "这条新闻在今日 AI 与科技信息中具有较高影响力。" : item.reason, true);
        addSection(page, "新闻摘要", item.summary.isEmpty()
                ? "当前来源未提供可核验的摘要，请阅读原文。" : item.summary, false);

        page.addView(UiKit.space(this, 24));
        TextView scoreLabel = UiKit.text(this, palette,
                "影响力 / 热度  " + item.score, 12, Typeface.BOLD);
        scoreLabel.setTextColor(palette.accent);
        page.addView(scoreLabel);
        page.addView(UiKit.space(this, 7));
        page.addView(UiKit.scoreBar(this, palette, item.score));
        TextView scoreNote = UiKit.text(this, palette,
                "综合来源质量、多源重合、话题相关性和信息热度的简化评分。",
                11, Typeface.NORMAL);
        scoreNote.setTextColor(palette.muted);
        scoreNote.setPadding(0, UiKit.dp(this, 8), 0, 0);
        page.addView(scoreNote);

        page.addView(UiKit.space(this, 30));
        TextView original = primaryButton("阅读原文");
        original.setOnClickListener(view -> {
            Intent intent = new Intent(this, WebArticleActivity.class);
            intent.putExtra("url", item.url);
            intent.putExtra("title", item.primaryTitle());
            startActivity(intent);
        });
        page.addView(original);

        TextView disclaimer = UiKit.text(this, palette,
                "AI 摘要用于快速了解内容，可能遗漏语境。引用或决策前请核对原文。",
                11, Typeface.NORMAL);
        disclaimer.setTextColor(palette.muted);
        disclaimer.setGravity(Gravity.CENTER);
        disclaimer.setPadding(0, UiKit.dp(this, 16), 0, 0);
        page.addView(disclaimer);

        setContentView(scroll);
    }

    private void addSection(LinearLayout page, String heading, String body, boolean accent) {
        page.addView(UiKit.space(this, 24));
        TextView title = UiKit.text(this, palette, heading, 12, Typeface.BOLD);
        title.setTextColor(accent ? palette.accent : palette.muted);
        title.setLetterSpacing(0.08f);
        page.addView(title);
        TextView content = UiKit.serif(this, palette, body, 18, Typeface.NORMAL);
        content.setLineSpacing(UiKit.dp(this, 4), 1.18f);
        content.setPadding(0, UiKit.dp(this, 10), 0, 0);
        page.addView(content);
    }

    private TextView action(String label) {
        TextView button = UiKit.text(this, palette, label, 12, Typeface.BOLD);
        button.setTextColor(palette.accent);
        button.setGravity(Gravity.CENTER);
        button.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 8),
                UiKit.dp(this, 8), UiKit.dp(this, 8));
        return button;
    }

    private TextView primaryButton(String label) {
        TextView button = UiKit.text(this, palette, label, 14, Typeface.BOLD);
        button.setTextColor(palette.paper);
        button.setGravity(Gravity.CENTER);
        button.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 14),
                UiKit.dp(this, 16), UiKit.dp(this, 14));
        button.setBackground(UiKit.rounded(palette.accent, 0, 3, this));
        return button;
    }

    private String formatMultiSource() {
        return item.sourceCount > 1 ? " · " + item.sourceCount + " 个来源" : "";
    }

    private String formatTime() {
        return item.publishedAt.isEmpty() ? "" : "\n" + item.publishedAt;
    }

    private void share() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        String text = item.primaryTitle()
                + (item.translationTitle().isEmpty() ? "" : "\n" + item.translationTitle())
                + "\n\n" + item.summary + "\n\n" + item.url;
        share.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, "分享新闻"));
    }
}
