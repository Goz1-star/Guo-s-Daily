package com.guo.daily;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.TimeZone;

final class AppScheduler {
    private static final long TWO_HOURS = 2L * 60 * 60 * 1000;

    static void scheduleAll(Context context) {
        scheduleRefresh(context);
        boolean enabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("notifications", true);
        if (enabled) scheduleNextDaily(context);
        else cancelDaily(context);
    }

    static void scheduleNextDaily(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        Calendar next = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        next.set(Calendar.HOUR_OF_DAY, 7);
        next.set(Calendar.MINUTE, 30);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        PendingIntent pending = dailyIntent(context, false);
        if (Build.VERSION.SDK_INT >= 31 && alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pending);
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pending);
        }
    }

    static void scheduleRetry(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        long retryAt = System.currentTimeMillis() + 15L * 60 * 1000;
        PendingIntent pending = dailyIntent(context, true);
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, retryAt, pending);
    }

    private static void scheduleRefresh(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        Intent intent = new Intent(context, RefreshReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, 2001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 60_000L, TWO_HOURS, pending);
    }

    private static void cancelDaily(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) alarm.cancel(dailyIntent(context, false));
    }

    private static PendingIntent dailyIntent(Context context, boolean retry) {
        Intent intent = new Intent(context, DailyDigestReceiver.class);
        intent.putExtra("retry", retry);
        return PendingIntent.getBroadcast(context, retry ? 7301 : 7300, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private AppScheduler() {}
}
