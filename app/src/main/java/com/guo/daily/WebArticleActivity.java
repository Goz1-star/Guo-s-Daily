package com.guo.daily;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class WebArticleActivity extends Activity {
    private ThemePalette palette;
    private WebView webView;
    private ProgressBar progress;
    private FrameLayout browser;
    private String url;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = new ThemePalette(this);
        ThemePalette.applySystemBars(this, palette);
        url = getIntent().getStringExtra("url");
        if (url == null || url.isEmpty()) {
            finish();
            return;
        }
        build();
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.paper);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 7),
                UiKit.dp(this, 10), UiKit.dp(this, 7));
        TextView back = toolbarButton("←");
        back.setOnClickListener(view -> {
            if (webView.canGoBack()) webView.goBack();
            else finish();
        });
        bar.addView(back);
        TextView title = UiKit.serif(this, palette, "原文", 18, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView external = toolbarButton("浏览器");
        external.setOnClickListener(view ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        bar.addView(external);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 54)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(palette.accent));
        progress.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(palette.line));
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 2)));

        browser = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(palette.paper);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }
        });
        browser.addView(webView);
        root.addView(browser, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        webView.loadUrl(url);
    }

    private TextView toolbarButton(String label) {
        TextView button = UiKit.text(this, palette, label, 12, Typeface.BOLD);
        button.setTextColor(palette.accent);
        button.setGravity(Gravity.CENTER);
        button.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 8),
                UiKit.dp(this, 10), UiKit.dp(this, 8));
        return button;
    }

    private void showError() {
        browser.removeAllViews();
        LinearLayout state = new LinearLayout(this);
        state.setOrientation(LinearLayout.VERTICAL);
        state.setGravity(Gravity.CENTER);
        state.setPadding(UiKit.dp(this, 28), UiKit.dp(this, 28),
                UiKit.dp(this, 28), UiKit.dp(this, 28));
        state.addView(UiKit.serif(this, palette, "原文暂时无法载入", 24, Typeface.BOLD));
        TextView hint = UiKit.text(this, palette,
                "请检查网络连接，或使用系统浏览器打开。", 14, Typeface.NORMAL);
        hint.setTextColor(palette.muted);
        hint.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 18));
        state.addView(hint);
        TextView retry = toolbarButton("重新载入");
        retry.setBackground(UiKit.rounded(palette.surface, palette.line, 3, this));
        retry.setOnClickListener(view -> {
            browser.removeAllViews();
            browser.addView(webView);
            webView.loadUrl(url);
        });
        state.addView(retry);
        browser.addView(state);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
