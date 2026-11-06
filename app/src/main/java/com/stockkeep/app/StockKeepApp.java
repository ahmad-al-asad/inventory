package com.stockkeep.app;

import android.app.Application;

import com.google.android.material.color.DynamicColors;
import com.stockkeep.app.notify.ExpiryWorker;
import com.stockkeep.app.notify.Notifier;

public class StockKeepApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.applyTheme(this);
        // Uses the wallpaper-based colours on Android 12+.
        DynamicColors.applyToActivitiesIfAvailable(this);
        Notifier.createChannels(this);
        ExpiryWorker.schedule(this);
    }
}
