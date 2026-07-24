package com.guo.daily;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;

final class UiKit {
    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context context, ThemePalette p, String value, float size, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(p.ink);
        view.setTextSize(size);
        view.setTypeface(style == Typeface.NORMAL ? Typeface.create("sans", style)
                : Typeface.create("sans", style));
        view.setLineSpacing(0, 1.1f);
        return view;
    }

    static TextView serif(Context context, ThemePalette p, String value, float size, int style) {
        TextView view = text(context, p, value, size, style);
        view.setTypeface(Typeface.create("serif", style));
        view.setLineSpacing(0, 1.05f);
        return view;
    }

    static View divider(Context context, ThemePalette p) {
        View divider = new View(context);
        divider.setBackgroundColor(p.line);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return divider;
    }

    static Space space(Context context, int height) {
        Space space = new Space(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, height)));
        return space;
    }

    static GradientDrawable rounded(int fill, int stroke, int radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (stroke != 0) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    static TextView chip(Context context, ThemePalette p, String label) {
        TextView chip = text(context, p, label, 11, Typeface.BOLD);
        chip.setTextColor(p.accent);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
        chip.setBackground(rounded(p.accentSoft, 0, 3, context));
        return chip;
    }

    static ProgressBar scoreBar(Context context, ThemePalette p, int score) {
        ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(Math.max(0, Math.min(100, score)));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(p.accent));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(p.line));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 3)));
        return bar;
    }

    private UiKit() {}
}
