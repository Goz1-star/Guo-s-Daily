package com.guo.daily;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.List;

public class DailyDigestReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "daily_digest";
    private static final int NOTIFICATION_ID = 730;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("notifications", true)) return;
        PendingResult pendingResult = goAsync();
        NewsRepository.refresh(context, new NewsRepository.Callback() {
            @Override
            public void onSuccess(List<NewsItem> items, boolean aiEnhanced) {
                showNotification(context, items, aiEnhanced);
                AppScheduler.scheduleNextDaily(context);
                pendingResult.finish();
            }

            @Override
            public void onError(String message, List<NewsItem> fallback) {
                showNotification(context, fallback, false);
                AppScheduler.scheduleRetry(context);
                AppScheduler.scheduleNextDaily(context);
                pendingResult.finish();
            }
        });
    }

    private void showNotification(Context context, List<NewsItem> items, boolean aiEnhanced) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "每日 AI 科技日报", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("每天早上 7:30 推送 Guo 的日报");
        manager.createNotificationChannel(channel);
        String lead = items.isEmpty() ? "今日日报已生成"
                : items.get(0).primaryTitle();
        String body = "今日精选 " + items.size() + " 条"
                + (aiEnhanced ? " · DeepSeek V4 Pro 已筛选" : "") + "\n" + lead;
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 730, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder builder =
                new android.app.Notification.Builder(context, CHANNEL);
        builder.setSmallIcon(com.guo.daily.R.drawable.ic_notification)
                .setContentTitle("Guo 的日报")
                .setContentText(body)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(android.app.Notification.CATEGORY_RECOMMENDATION);
        manager.notify(NOTIFICATION_ID, builder.build());
    }
}
