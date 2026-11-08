package com.stockkeep.app.notify;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.stockkeep.app.Prefs;
import com.stockkeep.app.R;
import com.stockkeep.app.data.AppDatabase;
import com.stockkeep.app.data.ItemWithBatches;
import com.stockkeep.app.logic.DateUtil;
import com.stockkeep.app.logic.Expiry;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs once a day (around 9:00) and posts one notification listing expired / soon-to-expire items. */
public class ExpiryWorker extends Worker {
    private static final String WORK_NAME = "daily-expiry-check";

    public ExpiryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        if (!Prefs.expiryNotifications(c)) return Result.success();

        long today = DateUtil.today();
        int soonDays = Prefs.soonDays(c);
        List<String> expired = new ArrayList<>();
        List<String> soon = new ArrayList<>();
        for (ItemWithBatches iwb : AppDatabase.get(c).dao().getAll()) {
            if (iwb.item.archived) continue;
            Long date = Expiry.soonest(iwb);
            Expiry.Status s = Expiry.status(date, today, soonDays);
            if (s == Expiry.Status.EXPIRED) {
                expired.add(iwb.item.name);
            } else if (s == Expiry.Status.SOON) {
                soon.add(c.getString(R.string.notif_line_soon, iwb.item.name, DateUtil.relative(c, date, today)));
            }
        }
        Notifier.showExpiry(c, expired, soon);
        return Result.success();
    }

    public static void schedule(@NonNull Context c) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = LocalDate.now().atTime(9, 0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        long delay = Duration.between(now, next).toMillis();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ExpiryWorker.class, 1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
