package com.guo.daily;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

public class RefreshReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        NewsRepository.refresh(context, new NewsRepository.Callback() {
            @Override
            public void onSuccess(List<NewsItem> items, boolean aiEnhanced) {
                result.finish();
            }

            @Override
            public void onError(String message, List<NewsItem> fallback) {
                result.finish();
            }
        });
    }
}
