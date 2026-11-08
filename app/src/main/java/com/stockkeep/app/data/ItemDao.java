package com.stockkeep.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
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

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM batches WHERE itemId = :itemId")
    int totalFor(long itemId);
}
