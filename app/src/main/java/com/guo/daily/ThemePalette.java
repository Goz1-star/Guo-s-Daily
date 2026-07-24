package com.guo.daily;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

final class ThemePalette {
    final boolean dark;
    final int paper;
    final int surface;
    final int ink;
    final int muted;
    final int line;
    final int accent;
    final int accentSoft;
    final int error;

    ThemePalette(Context context) {
        String mode = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("theme_mode", "system");
        boolean systemDark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        dark = "dark".equals(mode) || ("system".equals(mode) && systemDark);
        paper = Color.parseColor(dark ? "#111315" : "#FAFAF7");
        surface = Color.parseColor(dark ? "#181B1E" : "#FFFFFF");
        ink = Color.parseColor(dark ? "#F3F0E8" : "#121212");
        muted = Color.parseColor(dark ? "#A8A7A2" : "#65645F");
        line = Color.parseColor(dark ? "#35383C" : "#D7D4CD");
        accent = Color.parseColor(dark ? "#D1B173" : "#142238");
        accentSoft = Color.parseColor(dark ? "#29261E" : "#EEF1F5");
        error = Color.parseColor(dark ? "#FFB4AB" : "#A8322D");
    }

    static void applySystemBars(Activity activity, ThemePalette palette) {
        Window window = activity.getWindow();
        window.setStatusBarColor(palette.paper);
        window.setNavigationBarColor(palette.paper);
        if (Build.VERSION.SDK_INT >= 30) {
            window.getDecorView().getWindowInsetsController()
                    .setSystemBarsAppearance(
                            palette.dark ? 0 : android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        } else if (!palette.dark) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private ThemePalette(boolean unused) {
        dark = false;
        paper = surface = ink = muted = line = accent = accentSoft = error = 0;
    }
}
