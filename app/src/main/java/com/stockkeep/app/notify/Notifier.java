package com.stockkeep.app.notify;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.stockkeep.app.R;
import com.stockkeep.app.data.Item;
import com.stockkeep.app.data.Link;
import com.stockkeep.app.ui.DetailActivity;
import com.stockkeep.app.ui.MainActivity;

import java.util.List;

public final class Notifier {
    public static final String CHANNEL_STOCK = "stock";
    public static final String CHANNEL_EXPIRY = "expiry";
    private static final int EXPIRY_NOTIFICATION_ID = 1;
    private static final int PI_FLAGS = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

    private Notifier() {
    }

    public static void createChannels(@NonNull Context c) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        nm.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_STOCK,
                NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(c.getString(R.string.channel_stock))
                .setDescription(c.getString(R.string.channel_stock_desc))
                .build());
        nm.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_EXPIRY,
                NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(c.getString(R.string.channel_expiry))
                .setDescription(c.getString(R.string.channel_expiry_desc))
                .build());
    }

    /** Called after every stock change with the item's total quantity before and after. */
    public static void onStockChanged(@NonNull Context c, @NonNull Item item, @NonNull List<Link> links,
                                      int before, int after) {
        if (after == before || item.archived) return;
        if (after == 0 && before > 0 && item.notifyOut) {
            postStock(c, item, links, c.getString(R.string.notif_out_title, item.title()), c.getString(R.string.notif_out_text));
        } else if (after == 1 && before > 1 && item.notifyLow) {
            postStock(c, item, links, c.getString(R.string.notif_low_title, item.title()), c.getString(R.string.notif_low_text));
        }
    }

    private static void postStock(Context c, Item item, List<Link> links, String title, String text) {
        if (!canPost(c)) return;
        Intent open = DetailActivity.intent(c, item.id);
        PendingIntent content = TaskStackBuilder.create(c)
                .addNextIntentWithParentStack(open)
                .getPendingIntent((int) item.id * 2, PI_FLAGS);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_STOCK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(content)
                .setAutoCancel(true);

        if (links.size() == 1) {
            // One shop: the button opens it directly.
            Intent buy = new Intent(Intent.ACTION_VIEW, Uri.parse(links.get(0).url));
            PendingIntent buyPi = PendingIntent.getActivity(c, (int) item.id * 2 + 1, buy, PI_FLAGS);
            b.addAction(R.drawable.ic_cart, c.getString(R.string.action_buy), buyPi);
        } else if (links.size() > 1) {
            // Several shops: the button opens the item, where all the shops are listed.
            b.addAction(R.drawable.ic_cart, c.getString(R.string.action_where_to_buy), content);
        }
        notify(c, 1000 + (int) item.id, b);
    }

    public static void showExpiry(@NonNull Context c, @NonNull List<String> expired, @NonNull List<String> soon) {
        if (!canPost(c) || (expired.isEmpty() && soon.isEmpty())) return;
        String title;
        if (!expired.isEmpty() && !soon.isEmpty()) {
            title = c.getString(R.string.notif_expiry_both, expired.size(), soon.size());
        } else if (!expired.isEmpty()) {
            title = c.getResources().getQuantityString(R.plurals.notif_expired_count, expired.size(), expired.size());
        } else {
            title = c.getResources().getQuantityString(R.plurals.notif_soon_count, soon.size(), soon.size());
        }

        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        StringBuilder summary = new StringBuilder();
        for (String name : expired) {
            style.addLine(c.getString(R.string.notif_line_expired, name));
            if (summary.length() > 0) summary.append(", ");
            summary.append(name);
        }
        for (String line : soon) {
            style.addLine(line);
            if (summary.length() > 0) summary.append(", ");
            summary.append(line);
        }

        Intent open = new Intent(c, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(c, 0, open, PI_FLAGS);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_EXPIRY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(summary)
                .setStyle(style)
                .setContentIntent(content)
                .setAutoCancel(true);
        notify(c, EXPIRY_NOTIFICATION_ID, b);
    }

    private static boolean canPost(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    private static void notify(Context c, int id, NotificationCompat.Builder b) {
        try {
            NotificationManagerCompat.from(c).notify(id, b.build());
        } catch (SecurityException ignored) {
            // permission was revoked
        }
    }
}
