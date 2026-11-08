package com.stockkeep.app.data;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ItemWithBatches {
    @Embedded
    public Item item;

    @Relation(parentColumn = "id", entityColumn = "itemId")
    public List<Batch> batches = new ArrayList<>();

    @Relation(parentColumn = "id", entityColumn = "itemId")
    public List<Link> links = new ArrayList<>();

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

    /** Links in the order the user put them. */
    @NonNull
    public List<Link> sortedLinks() {
        List<Link> out = new ArrayList<>(links);
        out.sort(Comparator.comparingInt((Link l) -> l.position).thenComparingLong(l -> l.id));
        return out;
    }

    @NonNull
    public String contentKey() {
        StringBuilder sb = new StringBuilder(item.contentKey());
        for (Batch b : batches) sb.append('#').append(b.contentKey());
        for (Link l : links) sb.append('@').append(l.contentKey());
        return sb.toString();
    }
}
