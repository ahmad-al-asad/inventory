package com.stockkeep.app.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.color.MaterialColors;
import com.stockkeep.app.R;
import com.stockkeep.app.data.ImageStore;
import com.stockkeep.app.logic.Expiry;

final class UiUtil {
    private UiUtil() {
    }

    /** Adds https:// if missing. Returns null for empty input. */
    @Nullable
    static String normalizeLink(@Nullable String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (!s.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) s = "https://" + s;
        return s;
    }

    static boolean isValidLink(@NonNull String link) {
        Uri uri = Uri.parse(link);
        String host = uri.getHost();
        return uri.getScheme() != null && host != null && host.contains(".") && !host.startsWith(".");
    }

    static void openLink(@NonNull Context c, @Nullable String link) {
        if (TextUtils.isEmpty(link)) return;
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(c, R.string.no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shows the item's photo inside its square frame. The whole photo is always visible (fit inside),
     * with white around it if it isn't square. Without a photo, a neutral placeholder icon is shown.
     */
    static void loadPhoto(@NonNull ImageView view, @Nullable String photo) {
        Context c = view.getContext();
        if (photo != null) {
            view.setImageTintList(null);
            view.setBackgroundColor(Color.WHITE);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Glide.with(view).load(ImageStore.file(c, photo)).fitCenter().into(view);
        } else {
            Glide.with(view).clear(view);
            view.setBackgroundColor(MaterialColors.getColor(view,
                    com.google.android.material.R.attr.colorSurfaceContainerHighest));
            view.setScaleType(ImageView.ScaleType.CENTER);
            view.setImageResource(R.drawable.ic_inventory_large);
            view.setImageTintList(ColorStateList.valueOf(
                    MaterialColors.getColor(view, com.google.android.material.R.attr.colorOutline)));
        }
    }

    static int statusColor(@NonNull Context c, @NonNull Expiry.Status s) {
        switch (s) {
            case EXPIRED:
                return ContextCompat.getColor(c, R.color.status_expired);
            case SOON:
                return ContextCompat.getColor(c, R.color.status_soon);
            case OK:
                return ContextCompat.getColor(c, R.color.status_ok);
            default:
                return ContextCompat.getColor(c, R.color.status_none);
        }
    }

    static int statusTextColor(@NonNull Context c, @NonNull Expiry.Status s) {
        return ContextCompat.getColor(c, s == Expiry.Status.SOON ? R.color.status_on_soon : R.color.status_on_strong);
    }

    static void styleBadge(@NonNull TextView badge, @NonNull Expiry.Status s, @NonNull String text) {
        Context c = badge.getContext();
        badge.setText(text);
        badge.setBackgroundTintList(ColorStateList.valueOf(statusColor(c, s)));
        badge.setTextColor(statusTextColor(c, s));
    }

    static int parseInt(@Nullable CharSequence s, int fallback) {
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
