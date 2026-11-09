package com.stockkeep.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A folder for organising items, e.g. "Kitchen" or "Bathroom". */
@Entity(tableName = "categories")
public class Category {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** Reserved for custom ordering; folders are currently shown A–Z. */
    public int position;
}
