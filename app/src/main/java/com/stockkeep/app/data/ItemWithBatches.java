package com.stockkeep.app.data;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.ArrayList;
import java.util.List;

public class ItemWithBatches {
    @Embedded
    public Item item;

    @Relation(parentColumn = "id", entityColumn = "itemId")
    public List<Batch> batches = new ArrayList<>();

    public int totalQuantity() {
        int t = 0;
        for (Batch b : batches) t += Math.max(0, b.quantity);
        return t;
    }

    public int openedCount() {
        int t = 0;
        for (Batch b : batches) if (b.isOpened()) t += Math.max(0, b.quantity);
        return t;
    }

    @NonNull
    public String contentKey() {
        StringBuilder sb = new StringBuilder(item.contentKey());
        for (Batch b : batches) sb.append('#').append(b.contentKey());
        return sb.toString();
    }
}
