package com.stockkeep.app.data;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backup = one .zip file containing data.json (all items and batches) and the photos.
 * The format has a version number so future app versions can still read old backups.
 */
public final class BackupManager {
    private static final String FORMAT = "stockkeep-backup";
    private static final int FORMAT_VERSION = 4; // 2 = links + archived (1.2.0), 3 = folders (1.3.0), 4 = name = product name + displayName (1.3.1)
    private static final String DATA_ENTRY = "data.json";
    private static final String PHOTO_PREFIX = "photos/";

    private BackupManager() {
    }

    @WorkerThread
    public static int export(@NonNull Context c, @NonNull Uri destination) throws IOException, JSONException {
        List<ItemWithBatches> all = AppDatabase.get(c).dao().getAll();

        JSONArray items = new JSONArray();
        for (ItemWithBatches iwb : all) {
            Item it = iwb.item;
            JSONObject o = new JSONObject();
            o.put("id", it.id);
            o.put("name", it.name);
            o.put("displayName", it.displayName == null ? JSONObject.NULL : it.displayName);
            o.put("categoryId", it.categoryId == null ? JSONObject.NULL : it.categoryId);
            o.put("photo", it.photo == null ? JSONObject.NULL : it.photo);
            o.put("unit", it.unit == null ? JSONObject.NULL : it.unit);
            o.put("archived", it.archived);
            o.put("openLifeAmount", it.openLifeAmount == null ? JSONObject.NULL : it.openLifeAmount);
            o.put("openLifeUnit", it.openLifeUnit);
            o.put("notifyLow", it.notifyLow);
            o.put("notifyOut", it.notifyOut);
            o.put("notes", it.notes == null ? JSONObject.NULL : it.notes);
            o.put("createdAt", it.createdAt);
            JSONArray batches = new JSONArray();
            for (Batch b : iwb.batches) {
                JSONObject bo = new JSONObject();
                bo.put("id", b.id);
                bo.put("quantity", b.quantity);
                bo.put("purchaseDate", b.purchaseDate == null ? JSONObject.NULL : b.purchaseDate);
                bo.put("expiryDate", b.expiryDate == null ? JSONObject.NULL : b.expiryDate);
                bo.put("openedDate", b.openedDate == null ? JSONObject.NULL : b.openedDate);
                bo.put("createdAt", b.createdAt);
                batches.put(bo);
            }
            o.put("batches", batches);
            JSONArray links = new JSONArray();
            for (Link l : iwb.sortedLinks()) {
                JSONObject lo = new JSONObject();
                lo.put("label", l.label == null ? JSONObject.NULL : l.label);
                lo.put("url", l.url);
                links.put(lo);
            }
            o.put("links", links);
            items.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("version", FORMAT_VERSION);
        root.put("exportedAt", System.currentTimeMillis());
        root.put("items", items);
        JSONArray cats = new JSONArray();
        for (Category cat : AppDatabase.get(c).dao().getCategories()) {
            JSONObject co = new JSONObject();
            co.put("id", cat.id);
            co.put("name", cat.name);
            co.put("position", cat.position);
            cats.put(co);
        }
        root.put("categories", cats);

        OutputStream os = c.getContentResolver().openOutputStream(destination);
        if (os == null) throw new IOException("Cannot write to the chosen file");
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(os))) {
            zip.putNextEntry(new ZipEntry(DATA_ENTRY));
            zip.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (ItemWithBatches iwb : all) {
                String photo = iwb.item.photo;
                if (photo == null || !ImageStore.isSafeName(photo)) continue;
                File f = ImageStore.file(c, photo);
                if (!f.exists()) continue;
                zip.putNextEntry(new ZipEntry(PHOTO_PREFIX + photo));
                try (InputStream in = new FileInputStream(f)) {
                    copy(in, zip);
                }
                zip.closeEntry();
            }
        }
        return all.size();
    }

    /** Replaces everything in the app with the backup's contents. Returns the number of items restored. */
    @WorkerThread
    public static int restore(@NonNull Context c, @NonNull Uri source) throws IOException, JSONException {
        File staging = new File(c.getCacheDir(), "restore");
        deleteRecursive(staging);
        //noinspection ResultOfMethodCallIgnored
        staging.mkdirs();

        String json = null;
        InputStream is = c.getContentResolver().openInputStream(source);
        if (is == null) throw new IOException("Cannot open the chosen file");
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(is))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String n = e.getName();
                if (DATA_ENTRY.equals(n)) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    copy(zip, buf);
                    json = buf.toString("UTF-8");
                } else if (n.startsWith(PHOTO_PREFIX)) {
                    String fileName = n.substring(PHOTO_PREFIX.length());
                    if (!ImageStore.isSafeName(fileName)) continue;
                    try (OutputStream out = new FileOutputStream(new File(staging, fileName))) {
                        copy(zip, out);
                    }
                }
            }
        }
        if (json == null) throw new IOException("This file is not a StockKeep backup");

        JSONObject root = new JSONObject(json);
        if (!FORMAT.equals(root.optString("format"))) throw new IOException("This file is not a StockKeep backup");
        int version = root.optInt("version", 1);

        List<Item> items = new ArrayList<>();
        List<Batch> batches = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        List<Category> categories = new ArrayList<>();
        JSONArray catArr = root.optJSONArray("categories");
        if (catArr != null) {
            for (int i = 0; i < catArr.length(); i++) {
                JSONObject co = catArr.getJSONObject(i);
                Category cat = new Category();
                cat.id = co.getLong("id");
                cat.name = co.getString("name");
                cat.position = co.optInt("position", 0);
                categories.add(cat);
            }
        }
        JSONArray arr = root.getJSONArray("items");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Item it = new Item();
            it.id = o.getLong("id");
            it.name = o.getString("name");
            if (version >= 4) {
                it.displayName = optString(o, "displayName");
            } else {
                // Backups from 1.3.0 had "name" = display name and "productName" = product name.
                String product = optString(o, "productName");
                if (product != null && !product.trim().isEmpty() && !product.equals(it.name)) {
                    it.displayName = it.name;
                    it.name = product;
                }
            }
            it.categoryId = optLong(o, "categoryId");
            it.photo = optString(o, "photo");
            it.unit = optString(o, "unit");
            it.archived = o.optBoolean("archived", false);
            Long life = optLong(o, "openLifeAmount");
            it.openLifeAmount = life == null ? null : life.intValue();
            it.openLifeUnit = o.optInt("openLifeUnit", Item.LIFE_DAYS);
            it.notifyLow = o.optBoolean("notifyLow", true);
            it.notifyOut = o.optBoolean("notifyOut", true);
            it.notes = optString(o, "notes");
            it.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            items.add(it);

            JSONArray ls = o.optJSONArray("links");
            if (ls != null) {
                for (int j = 0; j < ls.length(); j++) {
                    JSONObject lo = ls.getJSONObject(j);
                    String url = optString(lo, "url");
                    if (url == null || url.trim().isEmpty()) continue;
                    Link l = new Link();
                    l.itemId = it.id;
                    l.label = optString(lo, "label");
                    l.url = url.trim();
                    l.position = j;
                    links.add(l);
                }
            } else {
                // Backups from app 1.0/1.1 had a single "buyLink".
                String old = optString(o, "buyLink");
                if (old != null && !old.trim().isEmpty()) {
                    Link l = new Link();
                    l.itemId = it.id;
                    l.url = old.trim();
                    links.add(l);
                }
            }

            JSONArray bs = o.optJSONArray("batches");
            if (bs == null) continue;
            for (int j = 0; j < bs.length(); j++) {
                JSONObject bo = bs.getJSONObject(j);
                Batch b = new Batch();
                b.id = bo.getLong("id");
                b.itemId = it.id;
                b.quantity = bo.getInt("quantity");
                b.purchaseDate = optLong(bo, "purchaseDate");
                b.expiryDate = optLong(bo, "expiryDate");
                b.openedDate = optLong(bo, "openedDate");
                b.createdAt = bo.optLong("createdAt", System.currentTimeMillis());
                batches.add(b);
            }
        }

        AppDatabase db = AppDatabase.get(c);
        ItemDao dao = db.dao();
        db.runInTransaction(() -> {
            dao.deleteAllLinks();
            dao.deleteAllBatches();
            dao.deleteAllItems();
            dao.deleteAllCategories();
            for (Category cat : categories) dao.insertCategory(cat);
            for (Item it : items) dao.insertItem(it);
            for (Batch b : batches) dao.insertBatch(b);
            if (!links.isEmpty()) dao.insertLinks(links);
        });

        // Swap the photo folder for the restored photos.
        File photos = ImageStore.dir(c);
        File[] old = photos.listFiles();
        if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
            f.delete();
        File[] restored = staging.listFiles();
        if (restored != null) {
            for (File f : restored) {
                File target = new File(photos, f.getName());
                if (!f.renameTo(target)) {
                    try (InputStream in = new FileInputStream(f); OutputStream out = new FileOutputStream(target)) {
                        copy(in, out);
                    }
                }
            }
        }
        deleteRecursive(staging);
        return items.size();
    }

    @Nullable
    private static String optString(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optString(key, null);
    }

    @Nullable
    private static Long optLong(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optLong(key);
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static void deleteRecursive(File f) {
        if (!f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File child : children) deleteRecursive(child);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
