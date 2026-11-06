package com.stockkeep.app.logic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.stockkeep.app.data.Batch;
import com.stockkeep.app.data.Item;
import com.stockkeep.app.data.ItemWithBatches;

import java.util.Comparator;

public final class Expiry {

    public enum Status { NONE, OK, SOON, EXPIRED }

    private Expiry() {
    }

    /**
     * The real "use by" date of a batch: the earlier of the printed expiry date and
     * (opened date + open life). Null if neither is known.
     */
    @Nullable
    public static Long effective(@NonNull Batch b, @NonNull Item item) {
        Long result = b.expiryDate;
        Integer life = item.openLifeDays();
        if (b.openedDate != null && life != null) {
            long openedLimit = b.openedDate + life;
            result = result == null ? openedLimit : Math.min(result, openedLimit);
        }
        return result;
    }

    @NonNull
    public static Status status(@Nullable Long date, long today, int soonDays) {
        if (date == null) return Status.NONE;
        if (date < today) return Status.EXPIRED;
        if (date - today <= soonDays) return Status.SOON;
        return Status.OK;
    }

    /** Soonest use-by date among the batches that are still in stock. */
    @Nullable
    public static Long soonest(@NonNull ItemWithBatches iwb) {
        Long best = null;
        for (Batch b : iwb.batches) {
            if (b.quantity <= 0) continue;
            Long e = effective(b, iwb.item);
            if (e != null && (best == null || e < best)) best = e;
        }
        return best;
    }

    /** Order in which units should be used: opened first, then soonest use-by date, then oldest. */
    @NonNull
    public static Comparator<Batch> useOrder(@NonNull Item item) {
        return (a, b) -> {
            if (a.isOpened() != b.isOpened()) return a.isOpened() ? -1 : 1;
            Long ea = effective(a, item);
            Long eb = effective(b, item);
            if (ea != null && eb == null) return -1;
            if (ea == null && eb != null) return 1;
            if (ea != null) {
                int cmp = Long.compare(ea, eb);
                if (cmp != 0) return cmp;
            }
            return Long.compare(a.createdAt, b.createdAt);
        };
    }
}
