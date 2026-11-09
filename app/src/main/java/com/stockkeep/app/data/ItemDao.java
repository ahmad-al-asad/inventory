package com.stockkeep.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ItemDao {

    @Transaction
    @Query("SELECT * FROM items ORDER BY name COLLATE NOCASE")
    LiveData<List<ItemWithBatches>> observeAll();

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    LiveData<ItemWithBatches> observe(long id);

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    ItemWithBatches get(long id);

    @Transaction
    @Query("SELECT * FROM items ORDER BY name COLLATE NOCASE")
    List<ItemWithBatches> getAll();

    @Query("SELECT * FROM items WHERE id = :id")
    Item getItem(long id);

    @Insert
    long insertItem(Item item);

    @Update
    void updateItem(Item item);

    @Delete
    void deleteItem(Item item);

    @Query("DELETE FROM items")
    void deleteAllItems();

    @Query("SELECT * FROM batches WHERE id = :id")
    Batch getBatch(long id);

    @Insert
    long insertBatch(Batch batch);

    @Update
    void updateBatch(Batch batch);

    @Delete
    void deleteBatch(Batch batch);

    @Query("DELETE FROM batches WHERE itemId = :itemId")
    void deleteBatchesFor(long itemId);

    @Query("DELETE FROM batches")
    void deleteAllBatches();

    @Query("SELECT * FROM links WHERE itemId = :itemId ORDER BY position, id")
    List<Link> getLinks(long itemId);

    @Insert
    void insertLinks(List<Link> links);

    @Query("DELETE FROM links WHERE itemId = :itemId")
    void deleteLinksFor(long itemId);

    @Query("DELETE FROM links")
    void deleteAllLinks();

    // ---------- folders ----------

    /** Default folder first, then the user's order. */
    @Query("SELECT * FROM categories ORDER BY isDefault DESC, position, name COLLATE NOCASE")
    LiveData<List<Category>> observeCategories();

    @Query("SELECT * FROM categories ORDER BY isDefault DESC, position, name COLLATE NOCASE")
    List<Category> getCategories();

    @Query("SELECT id FROM categories WHERE isDefault = 1 ORDER BY id LIMIT 1")
    Long getDefaultCategoryId();

    @Query("SELECT COALESCE(MAX(position), 0) FROM categories")
    int maxCategoryPosition();

    @Query("UPDATE categories SET position = :position WHERE id = :id")
    void setCategoryPosition(long id, int position);

    @Insert
    long insertCategory(Category c);

    @Update
    void updateCategory(Category c);

    @Query("DELETE FROM categories WHERE id = :id")
    void deleteCategory(long id);

    // ---------- folder membership ----------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertMemberships(List<ItemCategory> rows);

    @Query("DELETE FROM item_categories WHERE itemId = :itemId")
    void deleteMembershipsFor(long itemId);

    @Query("DELETE FROM item_categories WHERE itemId IN (:itemIds)")
    void deleteMembershipsForItems(List<Long> itemIds);

    @Query("DELETE FROM item_categories WHERE itemId IN (:itemIds) AND categoryId = :categoryId")
    void deleteMemberships(List<Long> itemIds, long categoryId);

    @Query("DELETE FROM item_categories")
    void deleteAllMemberships();

    @Query("SELECT categoryId FROM item_categories WHERE itemId = :itemId")
    List<Long> getFolderIds(long itemId);

    /** Items that are in no folder at all (they belong in the Default folder). */
    @Query("SELECT id FROM items WHERE id NOT IN (SELECT itemId FROM item_categories)")
    List<Long> itemsWithoutFolder();

    @Query("UPDATE items SET archived = :archived WHERE id IN (:itemIds)")
    void setArchived(List<Long> itemIds, boolean archived);

    @Query("DELETE FROM categories")
    void deleteAllCategories();

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM batches WHERE itemId = :itemId")
    int totalFor(long itemId);
}
