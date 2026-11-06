package com.stockkeep.app.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A group of units of one item that share the same dates.
 * Sealed units bought together are one batch; every opened unit is its own batch.
 * Dates are stored as "epoch days" (days since 1970-01-01).
 */
@Entity(tableName = "batches",
        foreignKeys = @ForeignKey(entity = Item.class, parentColumns = "id", childColumns = "itemId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("itemId"))
public class Batch {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long itemId;

    public int quantity;

    @Nullable
    public Long purchaseDate;

    @Nullable
    public Long expiryDate;

    /** Null while sealed. */
    @Nullable
    public Long openedDate;

    public long createdAt;

    public boolean isOpened() {
        return openedDate != null;
    }

    @NonNull
    public Batch copy() {
        Batch b = new Batch();
        b.id = id;
        b.itemId = itemId;
        b.quantity = quantity;
        b.purchaseDate = purchaseDate;
        b.expiryDate = expiryDate;
        b.openedDate = openedDate;
        b.createdAt = createdAt;
        return b;
    }

    @NonNull
    public String contentKey() {
        return id + ":" + quantity + ":" + purchaseDate + ":" + expiryDate + ":" + openedDate;
    }
}
