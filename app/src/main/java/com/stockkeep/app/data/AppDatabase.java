package com.stockkeep.app.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * HOW TO CHANGE THE DATABASE IN A FUTURE VERSION (without losing data):
 * 1. Change the entity classes (e.g. add a field to Item).
 * 2. Increase "version" below by 1.
 * 3. Add a Migration (see MIGRATION_1_2 below) and register it with .addMigrations(...) in get().
 * Never use fallbackToDestructiveMigration(): it would wipe the user's inventory.
 * The layout of every version is saved in app/schemas/ for reference.
 */
@Database(entities = {Item.class, Batch.class, Link.class, Category.class}, version = 3, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ItemDao dao();

    /** v1 -> v2 (app 1.2.0): adds "archived" to items and moves the single buy link into a list of links. */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `items` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE TABLE IF NOT EXISTS `links` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`itemId` INTEGER NOT NULL, `label` TEXT, `url` TEXT NOT NULL, `position` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_links_itemId` ON `links` (`itemId`)");
            db.execSQL("INSERT INTO `links` (`itemId`, `label`, `url`, `position`) "
                    + "SELECT `id`, NULL, `buyLink`, 0 FROM `items` WHERE `buyLink` IS NOT NULL AND `buyLink` != ''");
            db.execSQL("UPDATE `items` SET `buyLink` = NULL");
        }
    };

    /** v2 -> v3 (app 1.3.0): adds a product name and folders (categories). */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `items` ADD COLUMN `productName` TEXT");
            db.execSQL("ALTER TABLE `items` ADD COLUMN `categoryId` INTEGER");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_categoryId` ON `items` (`categoryId`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT NOT NULL, `position` INTEGER NOT NULL)");
        }
    };

    private static volatile AppDatabase instance;

    @NonNull
    public static AppDatabase get(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "stockkeep.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                }
            }
        }
        return instance;
    }
}
