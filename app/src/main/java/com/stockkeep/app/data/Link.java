package com.stockkeep.app.data;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A place to buy an item (a shop or seller). An item can have any number of these. */
@Entity(tableName = "links",
        foreignKeys = @ForeignKey(entity = Item.class, parentColumns = "id", childColumns = "itemId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("itemId"))
public class Link {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long itemId;

    /** Optional shop name, e.g. "Amazon". */
    @Nullable
    public String label;

    @NonNull
    public String url = "";

    /** Order in the list (0 = first). */
    public int position;

    /** The shop name, or the website name if no name was given. */
    @NonNull
    public String title() {
        if (label != null && !label.trim().isEmpty()) return label.trim();
        return host(url);
    }

    @NonNull
    public static String host(@NonNull String url) {
        String host = Uri.parse(url).getHost();
        if (host == null || host.isEmpty()) return url;
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    @NonNull
    public String contentKey() {
        return id + "~" + label + "~" + url + "~" + position;
    }
}
