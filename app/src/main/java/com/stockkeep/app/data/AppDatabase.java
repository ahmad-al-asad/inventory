package com.stockkeep.app.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * HOW TO CHANGE THE DATABASE IN A FUTURE VERSION (without losing data):
 * 1. Change the entity classes (e.g. add a field to Item).
 * 2. Increase "version" below by 1.
 * 3. Add a Migration, e.g.
 *      static final Migration MIGRATION_1_2 = new Migration(1, 2) {
 *          public void migrate(@NonNull SupportSQLiteDatabase db) {
 *              db.execSQL("ALTER TABLE items ADD COLUMN category TEXT");
 *          }
 *      };
 *    and register it with .addMigrations(MIGRATION_1_2) in get().
 * Never use fallbackToDestructiveMigration(): it would wipe the user's inventory.
 * The layout of every version is saved in app/schemas/ for reference.
 */
@Database(entities = {Item.class, Batch.class}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ItemDao dao();

    private static volatile AppDatabase instance;

    @NonNull
    public static AppDatabase get(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "stockkeep.db")
                            // .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}
