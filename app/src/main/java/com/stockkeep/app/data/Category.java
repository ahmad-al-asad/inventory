package com.stockkeep.app.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A folder for organising items, e.g. "Kitchen". An item can be in several folders.
 * There is always exactly one Default folder: it comes first, can't be deleted,
 * and holds every item that isn't in any other folder.
 */
@Entity(tableName = "categories")
public class Category {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** Order of the folder tabs (the Default folder is always first, whatever this says). */
    public int position;

    @ColumnInfo(defaultValue = "0")
    public boolean isDefault;
}
