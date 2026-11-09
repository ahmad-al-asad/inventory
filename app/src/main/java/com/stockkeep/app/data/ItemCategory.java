package com.stockkeep.app.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** "This item is in this folder." One row per item per folder. */
@Entity(tableName = "item_categories",
        primaryKeys = {"itemId", "categoryId"},
        foreignKeys = {
                @ForeignKey(entity = Item.class, parentColumns = "id", childColumns = "itemId",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Category.class, parentColumns = "id", childColumns = "categoryId",
                        onDelete = ForeignKey.CASCADE)},
        indices = @Index("categoryId"))
public class ItemCategory {
    public long itemId;
    public long categoryId;

    public ItemCategory() {
    }

    public ItemCategory(long itemId, long categoryId) {
        this.itemId = itemId;
        this.categoryId = categoryId;
    }
}
