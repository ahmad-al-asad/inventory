package com.stockkeep.app.logic;

import android.content.Context;

import androidx.annotation.NonNull;

import com.stockkeep.app.R;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** Dates are kept as epoch days (days since 1970-01-01), which avoids time-zone surprises. */
public final class DateUtil {
    public static final long DAY_MS = 86_400_000L;

    private static final DateTimeFormatter FULL = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault());

    private DateUtil() {
    }

    public static long today() {
        return LocalDate.now().toEpochDay();
    }

    @NonNull
    public static String format(long epochDay) {
        return LocalDate.ofEpochDay(epochDay).format(FULL);
    }

    @NonNull
    public static String formatShort(long epochDay) {
        return LocalDate.ofEpochDay(epochDay).format(SHORT);
    }

    /** MaterialDatePicker works in UTC milliseconds. */
    public static long toPickerMillis(long epochDay) {
        return epochDay * DAY_MS;
    }

    public static long fromPickerMillis(long utcMillis) {
        return Math.floorDiv(utcMillis, DAY_MS);
    }

    /** e.g. "expired 3 days ago", "today", "in 5 days". */
    @NonNull
    public static String relative(@NonNull Context c, long date, long today) {
        long d = date - today;
        if (d < -1) return c.getString(R.string.rel_expired_days_ago, -d);
        if (d == -1) return c.getString(R.string.rel_expired_yesterday);
        if (d == 0) return c.getString(R.string.rel_today);
        if (d == 1) return c.getString(R.string.rel_tomorrow);
        if (d <= 60) return c.getString(R.string.rel_in_days, d);
        return c.getString(R.string.rel_on_date, format(date));
    }

    /** Short text for the badge on a card. */
    @NonNull
    public static String badge(@NonNull Context c, long date, long today) {
        long d = date - today;
        if (d < 0) return c.getString(R.string.badge_expired);
        if (d == 0) return c.getString(R.string.badge_today);
        if (d == 1) return c.getString(R.string.badge_tomorrow);
        if (d <= 30) return c.getString(R.string.badge_days, d);
        return formatShort(date);
    }
}
