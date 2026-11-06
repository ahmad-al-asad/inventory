package com.stockkeep.app.data;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Photos are copied (and shrunk) into the app's private storage so they survive gallery clean-ups. */
public final class ImageStore {
    private static final int MAX_SIDE = 1600;

    private ImageStore() {
    }

    @NonNull
    public static File dir(@NonNull Context c) {
        File d = new File(c.getFilesDir(), "photos");
        if (!d.exists()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        return d;
    }

    @NonNull
    public static File file(@NonNull Context c, @NonNull String name) {
        return new File(dir(c), name);
    }

    /** A temporary file the camera app can write into. */
    @NonNull
    public static Uri newCameraUri(@NonNull Context c) {
        File d = new File(c.getCacheDir(), "camera");
        if (!d.exists()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        File f = new File(d, "capture-" + System.currentTimeMillis() + ".jpg");
        return FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", f);
    }

    /** Copies an image into app storage, fixing rotation and limiting size. Returns the new file name. */
    @Nullable
    @WorkerThread
    public static String importImage(@NonNull Context c, @NonNull Uri uri) {
        ContentResolver cr = c.getContentResolver();
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = cr.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = cr.openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return null;

            int rotation = 0;
            try (InputStream in = cr.openInputStream(uri)) {
                if (in != null) rotation = new ExifInterface(in).getRotationDegrees();
            } catch (IOException ignored) {
                // no EXIF data
            }

            float scale = Math.min(1f, (float) MAX_SIDE / Math.max(bmp.getWidth(), bmp.getHeight()));
            if (rotation != 0 || scale < 1f) {
                Matrix m = new Matrix();
                m.postScale(scale, scale);
                m.postRotate(rotation);
                Bitmap transformed = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                if (transformed != bmp) {
                    bmp.recycle();
                    bmp = transformed;
                }
            }

            String name = UUID.randomUUID() + ".jpg";
            try (FileOutputStream out = new FileOutputStream(file(c, name))) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }
            bmp.recycle();
            return name;
        } catch (IOException | SecurityException | OutOfMemoryError e) {
            return null;
        }
    }

    public static void delete(@NonNull Context c, @Nullable String name) {
        if (name == null || !isSafeName(name)) return;
        //noinspection ResultOfMethodCallIgnored
        file(c, name).delete();
    }

    public static boolean isSafeName(@NonNull String name) {
        return name.matches("[A-Za-z0-9._-]+") && !name.contains("..");
    }
}
