package com.stockkeep.app.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A product you keep in stock, e.g. "Olive oil". Things that are the same for every unit live here. */
@Entity(tableName = "items", indices = @Index("categoryId"))
public class Item {
    public static final int LIFE_DAYS = 0;
    public static final int LIFE_WEEKS = 1;
    public static final int LIFE_MONTHS = 2;

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Product name: the actual name, e.g. "Kirkland Extra Virgin Olive Oil 2 L". Always set. */
    @NonNull
    public String name = "";

    /**
     * Optional shorter name to show in the app, e.g. "Olive oil". When empty, the product name is shown.
     * (Stored in the column that app 1.3.0 called "productName"; migration 3 -> 4 sorted the values out.)
     */
    @ColumnInfo(name = "productName")
    @Nullable
    public String displayName;

    /** Folder this item is in, or null for none. */
    @Nullable
    public Long categoryId;

    /** File name of the photo inside the app's private "photos" folder. */
    @Nullable
    public String photo;

    @Nullable
    public String unit;

    /**
     * Only used by database version 1. Since version 2 an item's shops live in the "links" table.
     * Kept so the old column still matches; the migration moves its value into a Link.
     */
    @Nullable
    public String buyLink;

    /** How long it lasts once opened, e.g. 3 (amount) weeks (unit). Null = not set. */
    @Nullable
    public Integer openLifeAmount;

    public int openLifeUnit = LIFE_DAYS;

    public boolean notifyLow = true;

    public boolean notifyOut = true;

    @Nullable
    public String notes;

    public long createdAt;

    /** True when the item is no longer used. It's kept for the record but hidden from the main list. */
    @ColumnInfo(defaultValue = "0")
    public boolean archived;

    /** The name to show: the display name if there is one, otherwise the product name. */
    @NonNull
    public String title() {
        return displayName != null && !displayName.trim().isEmpty() ? displayName.trim() : name;
    }

    /** True when a separate display name is set (so the product name is worth showing too). */
    public boolean hasDisplayName() {
        return displayName != null && !displayName.trim().isEmpty() && !displayName.trim().equals(name);
    }

    /** Open life converted to days, or null if not set. */
    @Nullable
    public Integer openLifeDays() {
        if (openLifeAmount == null || openLifeAmount <= 0) return null;
        switch (openLifeUnit) {
            case LIFE_WEEKS:
                return openLifeAmount * 7;
            case LIFE_MONTHS:
                return openLifeAmount * 30;
            default:
                return openLifeAmount;
        }
    }

    @NonNull
    public String contentKey() {
        return id + "|" + name + "|" + displayName + "|" + categoryId + "|" + photo + "|" + unit + "|" + openLifeAmount + "|"
                + openLifeUnit + "|" + notifyLow + "|" + notifyOut + "|" + archived;
    }
}
