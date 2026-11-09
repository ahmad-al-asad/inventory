package com.stockkeep.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.stockkeep.app.logic.DateUtil;
import com.stockkeep.app.logic.Expiry;
import com.stockkeep.app.notify.Notifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** All reads and writes go through here. Writes run in order on one background thread. */
public class Repository {

    private static volatile Repository instance;

    private final Context app;
    private final AppDatabase db;
    private final ItemDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @NonNull
    public static Repository get(@NonNull Context context) {
        if (instance == null) {
            synchronized (Repository.class) {
                if (instance == null) instance = new Repository(context.getApplicationContext());
            }
        }
        return instance;
    }

    private Repository(Context app) {
        this.app = app;
        this.db = AppDatabase.get(app);
        this.dao = db.dao();
    }

    @NonNull
    public ExecutorService io() {
        return io;
    }

    public LiveData<List<ItemWithBatches>> observeAll() {
        return dao.observeAll();
    }

    public LiveData<ItemWithBatches> observe(long id) {
        return dao.observe(id);
    }

    // ---------- folders ----------

    public LiveData<List<Category>> observeCategories() {
        return dao.observeCategories();
    }

    public void addCategory(@NonNull String name, @Nullable Consumer<Long> done) {
        io.execute(() -> {
            Category c = new Category();
            c.name = name.trim();
            c.position = dao.maxCategoryPosition() + 1; // new folders go at the end
            long id = dao.insertCategory(c);
            if (done != null) main.post(() -> done.accept(id));
        });
    }

    public void renameCategory(@NonNull Category c, @NonNull String name) {
        io.execute(() -> {
            c.name = name.trim();
            dao.updateCategory(c);
        });
    }

    /** Deletes a folder (never the Default one). Items that were only in it move to the Default folder. */
    public void deleteCategory(long id) {
        io.execute(() -> db.runInTransaction(() -> {
            Long def = dao.getDefaultCategoryId();
            if (def != null && def == id) return;
            dao.deleteCategory(id); // memberships go with it
            fillDefault();
        }));
    }

    /** Saves the order of folders as shown (the Default folder stays first regardless). */
    public void reorderCategories(@NonNull List<Long> orderedIds) {
        io.execute(() -> db.runInTransaction(() -> {
            for (int i = 0; i < orderedIds.size(); i++) dao.setCategoryPosition(orderedIds.get(i), i + 1);
        }));
    }

    // ---------- folder membership ----------

    /** Puts items exactly in these folders (none = Default folder). */
    public void setFolders(@NonNull List<Long> itemIds, @NonNull Collection<Long> folderIds) {
        io.execute(() -> db.runInTransaction(() -> {
            dao.deleteMembershipsForItems(itemIds);
            insertMemberships(itemIds, folderIds);
            fillDefault();
        }));
    }

    /** Also puts items in this folder, keeping their other folders. Leaves the Default folder if it was the only one. */
    public void addToFolder(@NonNull List<Long> itemIds, long folderId) {
        io.execute(() -> db.runInTransaction(() -> {
            Long def = dao.getDefaultCategoryId();
            if (def != null && def != folderId) dao.deleteMemberships(itemIds, def);
            insertMemberships(itemIds, java.util.Collections.singletonList(folderId));
        }));
    }

    /** Takes items out of one folder. Items left with no folder go back to the Default folder. */
    public void removeFromFolder(@NonNull List<Long> itemIds, long folderId) {
        io.execute(() -> db.runInTransaction(() -> {
            dao.deleteMemberships(itemIds, folderId);
            fillDefault();
        }));
    }

    public void setArchived(@NonNull List<Long> itemIds, boolean archived) {
        io.execute(() -> dao.setArchived(itemIds, archived));
    }

    private void insertMemberships(Collection<Long> itemIds, Collection<Long> folderIds) {
        List<ItemCategory> rows = new ArrayList<>();
        for (Long item : itemIds) for (Long folder : folderIds) rows.add(new ItemCategory(item, folder));
        if (!rows.isEmpty()) dao.insertMemberships(rows);
    }

    /** Any item in no folder goes into the Default folder. */
    private void fillDefault() {
        Long def = dao.getDefaultCategoryId();
        if (def == null) return;
        insertMemberships(dao.itemsWithoutFolder(), java.util.Collections.singletonList(def));
    }

    // ---------- items ----------

    /** Adds a new item. With no folders chosen it goes into the Default folder. */
    public void createItem(@NonNull Item item, @NonNull List<Batch> batches, @NonNull List<Link> links,
                           @NonNull Collection<Long> folderIds, @Nullable Consumer<Long> done) {
        io.execute(() -> {
            long now = System.currentTimeMillis();
            item.createdAt = now;
            long[] id = new long[1];
            db.runInTransaction(() -> {
                id[0] = dao.insertItem(item);
                for (Batch b : batches) {
                    if (b.quantity <= 0) continue;
                    b.itemId = id[0];
                    b.createdAt = now;
                    dao.insertBatch(b);
                }
                insertLinks(id[0], links);
                insertMemberships(java.util.Collections.singletonList(id[0]), folderIds);
                fillDefault();
            });
            if (done != null) main.post(() -> done.accept(id[0]));
        });
    }

    /** Saves the item and replaces its list of shop links and its folders (none = Default folder). */
    public void updateItem(@NonNull Item item, @NonNull List<Link> links, @NonNull Collection<Long> folderIds) {
        io.execute(() -> db.runInTransaction(() -> {
            dao.updateItem(item);
            dao.deleteLinksFor(item.id);
            insertLinks(item.id, links);
            dao.deleteMembershipsFor(item.id);
            insertMemberships(java.util.Collections.singletonList(item.id), folderIds);
            fillDefault();
        }));
    }

    /** Marks an item as no longer used (kept for the record), or back in use. */
    public void setArchived(long itemId, boolean archived) {
        io.execute(() -> {
            Item item = dao.getItem(itemId);
            if (item == null) return;
            item.archived = archived;
            dao.updateItem(item);
        });
    }

    private void insertLinks(long itemId, List<Link> links) {
        List<Link> copy = new ArrayList<>();
        int pos = 0;
        for (Link l : links) {
            if (l.url.trim().isEmpty()) continue;
            Link n = new Link();
            n.itemId = itemId;
            n.label = l.label;
            n.url = l.url.trim();
            n.position = pos++;
            copy.add(n);
        }
        if (!copy.isEmpty()) dao.insertLinks(copy);
    }

    public void deleteItem(@NonNull Item item) {
        io.execute(() -> db.runInTransaction(() -> {
            dao.deleteBatchesFor(item.id);
            dao.deleteItem(item);
        }));
    }

    /** Puts back an item deleted a moment ago (undo). */
    public void restore(@NonNull ItemWithBatches iwb) {
        io.execute(() -> db.runInTransaction(() -> {
            dao.insertItem(iwb.item);
            for (Batch b : iwb.batches) dao.insertBatch(b);
            if (!iwb.links.isEmpty()) dao.insertLinks(iwb.links);
            insertMemberships(java.util.Collections.singletonList(iwb.item.id), iwb.folderIds);
            fillDefault();
        }));
    }

    // ---------- stock changes (these can trigger "1 left" / "out of stock" notifications) ----------

    public void addBatch(@NonNull Batch batch) {
        watchStock(batch.itemId, () -> {
            batch.createdAt = System.currentTimeMillis();
            if (batch.quantity > 0) dao.insertBatch(batch);
        });
    }

    public void updateBatch(@NonNull Batch batch) {
        watchStock(batch.itemId, () -> {
            if (batch.quantity <= 0) dao.deleteBatch(batch);
            else dao.updateBatch(batch);
        });
    }

    public void deleteBatch(@NonNull Batch batch) {
        watchStock(batch.itemId, () -> dao.deleteBatch(batch));
    }

    public void changeQuantity(@NonNull Batch batch, int delta) {
        watchStock(batch.itemId, () -> {
            Batch cur = dao.getBatch(batch.id);
            if (cur == null) return;
            cur.quantity += delta;
            if (cur.quantity <= 0) dao.deleteBatch(cur);
            else dao.updateBatch(cur);
        });
    }

    /** Uses one unit: opened units first, then the sealed batch that expires soonest. */
    public void useOne(long itemId) {
        watchStock(itemId, () -> {
            ItemWithBatches iwb = dao.get(itemId);
            if (iwb == null) return;
            List<Batch> inStock = new ArrayList<>();
            for (Batch b : iwb.batches) if (b.quantity > 0) inStock.add(b);
            if (inStock.isEmpty()) return;
            Collections.sort(inStock, Expiry.useOrder(iwb.item));
            Batch target = inStock.get(0);
            target.quantity--;
            if (target.quantity <= 0) dao.deleteBatch(target);
            else dao.updateBatch(target);
        });
    }

    /** Opens one sealed unit of the item: the one that expires soonest. */
    public void openNext(long itemId) {
        io.execute(() -> {
            ItemWithBatches iwb = dao.get(itemId);
            if (iwb == null) return;
            Batch best = null;
            java.util.Comparator<Batch> order = Expiry.useOrder(iwb.item);
            for (Batch b : iwb.batches) {
                if (b.quantity <= 0 || b.isOpened()) continue;
                if (best == null || order.compare(b, best) < 0) best = b;
            }
            if (best != null) openOne(best);
        });
    }

    /** Opens one unit of a sealed batch: it moves into its own batch with today's opened date. */
    public void openOne(@NonNull Batch batch) {
        watchStock(batch.itemId, () -> {
            Batch cur = dao.getBatch(batch.id);
            if (cur == null || cur.quantity <= 0 || cur.isOpened()) return;
            long today = DateUtil.today();
            if (cur.quantity == 1) {
                cur.openedDate = today;
                dao.updateBatch(cur);
            } else {
                cur.quantity--;
                dao.updateBatch(cur);
                Batch opened = cur.copy();
                opened.id = 0;
                opened.quantity = 1;
                opened.openedDate = today;
                opened.createdAt = System.currentTimeMillis();
                dao.insertBatch(opened);
            }
        });
    }

    private void watchStock(long itemId, @NonNull Runnable change) {
        io.execute(() -> {
            int before = dao.totalFor(itemId);
            db.runInTransaction(change);
            int after = dao.totalFor(itemId);
            Item item = dao.getItem(itemId);
            if (item != null) Notifier.onStockChanged(app, item, dao.getLinks(itemId), before, after);
        });
    }
}
