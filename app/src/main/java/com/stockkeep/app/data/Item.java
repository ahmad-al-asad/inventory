package com.stockkeep.app.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A product you keep in stock, e.g. "Olive oil". Things that are the same for every unit live here. */
@Entity(tableName = "items")
public class Item {
    public static final int LIFE_DAYS = 0;
    public static final int LIFE_WEEKS = 1;
    public static final int LIFE_MONTHS = 2;

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

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
        return id + "|" + name + "|" + photo + "|" + unit + "|" + openLifeAmount + "|"
                + openLifeUnit + "|" + notifyLow + "|" + notifyOut + "|" + archived;
    }
}
