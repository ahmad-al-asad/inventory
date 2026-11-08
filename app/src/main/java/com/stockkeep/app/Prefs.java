package com.stockkeep.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

public final class Prefs {
    public static final String KEY_SOON_DAYS = "soon_days";
    public static final String KEY_EXPIRY_NOTIF = "expiry_notifications";
    public static final String KEY_THEME = "theme";
    public static final String KEY_EXPORT = "export";
    public static final String KEY_IMPORT = "import";
    public static final String KEY_VERSION = "version";
    public static final String KEY_APP_LOCK = "app_lock";
    public static final String KEY_LOCK_TIMEOUT = "lock_timeout";
    private static final String KEY_SORT = "sort";
    private static final String KEY_ASKED_NOTIF = "asked_notification_permission";

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c);
    }

    public static int soonDays(Context c) {
        try {
            return Integer.parseInt(sp(c).getString(KEY_SOON_DAYS, "3"));
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    public static boolean expiryNotifications(Context c) {
        return sp(c).getBoolean(KEY_EXPIRY_NOTIF, true);
    }

    public static boolean appLock(Context c) {
        return sp(c).getBoolean(KEY_APP_LOCK, false);
    }

    public static void setAppLock(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_APP_LOCK, on).apply();
    }

    /** How long the app may stay in the background before asking again. */
    public static long lockTimeoutMs(Context c) {
        try {
            return Long.parseLong(sp(c).getString(KEY_LOCK_TIMEOUT, "0")) * 1000L;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int sort(Context c) {
        return sp(c).getInt(KEY_SORT, 0);
    }

    public static void setSort(Context c, int sort) {
        sp(c).edit().putInt(KEY_SORT, sort).apply();
    }

    public static boolean askedNotificationPermission(Context c) {
        return sp(c).getBoolean(KEY_ASKED_NOTIF, false);
    }

    public static void setAskedNotificationPermission(Context c) {
        sp(c).edit().putBoolean(KEY_ASKED_NOTIF, true).apply();
    }

    public static void applyTheme(Context c) {
        applyTheme(sp(c).getString(KEY_THEME, "system"));
    }

    public static void applyTheme(String value) {
        int mode;
        if ("light".equals(value)) mode = AppCompatDelegate.MODE_NIGHT_NO;
        else if ("dark".equals(value)) mode = AppCompatDelegate.MODE_NIGHT_YES;
        else mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
