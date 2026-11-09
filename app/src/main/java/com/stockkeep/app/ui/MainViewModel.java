package com.stockkeep.app.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.stockkeep.app.Prefs;
import com.stockkeep.app.data.Item;
import com.stockkeep.app.data.ItemWithBatches;
import com.stockkeep.app.data.Repository;
import com.stockkeep.app.logic.DateUtil;
import com.stockkeep.app.logic.Expiry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainViewModel extends AndroidViewModel {

    public enum Filter { ALL, SOON, EXPIRED, OPENED, LOW, ARCHIVED }

    public static final int SORT_EXPIRY = 0;
    public static final int SORT_NAME = 1;
    public static final int SORT_RECENT = 2;
    public static final int SORT_QUANTITY = 3;

    /** Folder shown: one folder's id, or every item in every folder. */
    public static final long CATEGORY_ALL = -1;

    private final LiveData<List<ItemWithBatches>> source;
    private long categoryFilter = CATEGORY_ALL;
    /** False until the app has picked the Default folder as the starting tab. */
    private boolean categoryChosen;
    private final MediatorLiveData<List<ItemWithBatches>> visible = new MediatorLiveData<>();
    private String query = "";
    private Filter filter = Filter.ALL;
    private int sort;

    public MainViewModel(@NonNull Application app) {
        super(app);
        source = Repository.get(app).observeAll();
        sort = Prefs.sort(app);
        visible.addSource(source, list -> recompute());
    }

    public LiveData<List<ItemWithBatches>> items() {
        return visible;
    }

    public boolean hasAnyItems() {
        List<ItemWithBatches> all = source.getValue();
        return all != null && !all.isEmpty();
    }

    public void setQuery(String q) {
        query = q == null ? "" : q;
        recompute();
    }

    public boolean isCategoryChosen() {
        return categoryChosen;
    }

    public void setCategory(long category) {
        categoryChosen = true;
        if (category == categoryFilter) return;
        categoryFilter = category;
        recompute();
    }

    public long getCategory() {
        return categoryFilter;
    }

    /** Active (not archived) item counts per folder id; key CATEGORY_ALL = all items. */
    @NonNull
    public Map<Long, Integer> activeCounts() {
        Map<Long, Integer> counts = new HashMap<>();
        List<ItemWithBatches> all = source.getValue();
        if (all == null) return counts;
        for (ItemWithBatches iwb : all) {
            if (iwb.item.archived) continue;
            for (Long f : iwb.folderIds) counts.merge(f, 1, Integer::sum);
            counts.merge(CATEGORY_ALL, 1, Integer::sum);
        }
        return counts;
    }

    /** Search looks at both the display name and the product name. */
    private static boolean matches(Item item, String q) {
        if (item.name.toLowerCase(Locale.ROOT).contains(q)) return true;
        return item.displayName != null && item.displayName.toLowerCase(Locale.ROOT).contains(q);
    }

    public void setFilter(Filter f) {
        filter = f;
        recompute();
    }

    public Filter getFilter() {
        return filter;
    }

    public void setSort(int s) {
        sort = s;
        Prefs.setSort(getApplication(), s);
        recompute();
    }

    public int getSort() {
        return sort;
    }

    /** Re-evaluates statuses (e.g. after the date changed or settings changed). */
    public void refresh() {
        recompute();
    }

    private void recompute() {
        List<ItemWithBatches> all = source.getValue();
        if (all == null) return;
        long today = DateUtil.today();
        int soonDays = Prefs.soonDays(getApplication());
        String q = query.trim().toLowerCase(Locale.ROOT);

        Map<Long, Long> soonest = new HashMap<>();
        List<ItemWithBatches> out = new ArrayList<>();
        for (ItemWithBatches iwb : all) {
            if (!q.isEmpty() && !matches(iwb.item, q)) continue;
            if (categoryFilter != CATEGORY_ALL && !iwb.inFolder(categoryFilter)) continue;
            // Archived (no longer used) items only show under the "Archived" filter.
            if (iwb.item.archived != (filter == Filter.ARCHIVED)) continue;
            Long date = Expiry.soonest(iwb);
            soonest.put(iwb.item.id, date);
            Expiry.Status s = Expiry.status(date, today, soonDays);
            switch (filter) {
                case SOON:
                    if (s != Expiry.Status.SOON) continue;
                    break;
                case EXPIRED:
                    if (s != Expiry.Status.EXPIRED) continue;
                    break;
                case OPENED:
                    if (iwb.openedCount() == 0) continue;
                    break;
                case LOW:
                    if (iwb.totalQuantity() > 1) continue;
                    break;
                default:
                    break;
            }
            out.add(iwb);
        }

        Comparator<ItemWithBatches> byName = (a, b) -> a.item.title().compareToIgnoreCase(b.item.title());
        Comparator<ItemWithBatches> cmp;
        switch (sort) {
            case SORT_NAME:
                cmp = byName;
                break;
            case SORT_RECENT:
                cmp = (a, b) -> Long.compare(b.item.createdAt, a.item.createdAt);
                break;
            case SORT_QUANTITY:
                cmp = Comparator.<ItemWithBatches>comparingInt(ItemWithBatches::totalQuantity).thenComparing(byName);
                break;
            default:
                cmp = (a, b) -> {
                    Long ea = soonest.get(a.item.id);
                    Long eb = soonest.get(b.item.id);
                    if (ea != null && eb == null) return -1;
                    if (ea == null && eb != null) return 1;
                    if (ea != null && !ea.equals(eb)) return Long.compare(ea, eb);
                    return byName.compare(a, b);
                };
                break;
        }
        out.sort(cmp);
        visible.setValue(out);
    }
}
