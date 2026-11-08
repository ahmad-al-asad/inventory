package com.stockkeep.app.security;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;

import com.stockkeep.app.Prefs;
import com.stockkeep.app.ui.LockActivity;

/**
 * Optional app lock using the phone's own screen lock (fingerprint / face, or the phone's PIN, pattern or password).
 * The app is locked when it starts, and again when it comes back after being in the background
 * for longer than the chosen timeout.
 */
public final class AppLock implements Application.ActivityLifecycleCallbacks {

    /** Fingerprint/face, with the phone's PIN/pattern/password as a fallback. */
    public static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    /** Coming back from the camera, photo picker or a file picker is treated more gently. */
    private static final long PICKER_GRACE_MS = 10 * 60 * 1000L;

    private static AppLock instance;

    private final Application app;
    private int startedActivities;
    private boolean unlocked;
    private long backgroundAt;
    private boolean configChange;
    private boolean pickerOpen;

    private AppLock(Application app) {
        this.app = app;
    }

    public static void install(@NonNull Application app) {
        if (instance != null) return;
        instance = new AppLock(app);
        app.registerActivityLifecycleCallbacks(instance);
    }

    @NonNull
    public static AppLock get() {
        if (instance == null) throw new IllegalStateException("AppLock not installed");
        return instance;
    }

    /** True if the phone has a fingerprint, face or screen lock that can be used. */
    public static boolean isAvailable(@NonNull Context c) {
        return BiometricManager.from(c).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /** Call right before opening the camera, gallery or a file picker, so returning doesn't ask again. */
    public static void openingPicker() {
        if (instance != null) instance.pickerOpen = true;
    }

    public void markUnlocked() {
        unlocked = true;
        backgroundAt = 0;
    }

    private boolean needsUnlock() {
        return Prefs.appLock(app) && !unlocked;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) {
        // Hide the app's content in the recent-apps screen while the lock is on (Android 13+).
        if (Build.VERSION.SDK_INT >= 33) {
            activity.setRecentsScreenshotEnabled(!Prefs.appLock(app));
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (startedActivities++ == 0) {
            // The app is coming back to the foreground.
            if (!configChange && unlocked && backgroundAt > 0) {
                long limit = Prefs.lockTimeoutMs(app);
                if (pickerOpen) limit = Math.max(limit, PICKER_GRACE_MS);
                if (SystemClock.elapsedRealtime() - backgroundAt >= limit) unlocked = false;
            }
            configChange = false;
            pickerOpen = false;
            backgroundAt = 0;
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity instanceof LockActivity || !needsUnlock()) return;
        activity.startActivity(new Intent(activity, LockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (--startedActivities <= 0) {
            startedActivities = 0;
            if (activity.isChangingConfigurations()) {
                configChange = true; // e.g. screen rotation, not really leaving the app
            } else if (unlocked) {
                backgroundAt = SystemClock.elapsedRealtime();
            }
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle out) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
