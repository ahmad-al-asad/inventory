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
@Database(entities = {Item.class, Batch.class, Link.class, Category.class, ItemCategory.class}, version = 5,
        exportSchema = true)
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

    /**
     * v3 -> v4 (app 1.3.1): no layout change. 1.3.0 put existing names into the display name; they are
     * really product names. Afterwards "name" = product name and "productName" column = optional display name.
     *  - Only one name: it stays as the product name, display name empty.
     *  - Both names typed in 1.3.0 (display in "name", product in "productName"): swapped into place.
     */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("UPDATE `items` SET `name` = `productName`, `productName` = `name` "
                    + "WHERE `productName` IS NOT NULL AND TRIM(`productName`) != '' AND `productName` != `name`");
            db.execSQL("UPDATE `items` SET `productName` = NULL "
                    + "WHERE `productName` IS NOT NULL AND (TRIM(`productName`) = '' OR `productName` = `name`)");
        }
    };

    /**
     * v4 -> v5 (app 1.4.0): items can be in several folders, there is a Default folder,
     * and folders have a user-chosen order.
     */
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `isDefault` INTEGER NOT NULL DEFAULT 0");
            // Keep the current A-Z order of existing folders as their starting order (positions 1, 2, 3...).
            db.execSQL("UPDATE `categories` SET `position` = 1 + (SELECT COUNT(*) FROM `categories` AS c2 "
                    + "WHERE c2.`name` < `categories`.`name` COLLATE NOCASE "
                    + "OR (c2.`name` = `categories`.`name` COLLATE NOCASE AND c2.`id` < `categories`.`id`))");
            db.execSQL(INSERT_DEFAULT_IF_MISSING);
            db.execSQL("CREATE TABLE IF NOT EXISTS `item_categories` (`itemId` INTEGER NOT NULL, "
                    + "`categoryId` INTEGER NOT NULL, PRIMARY KEY(`itemId`, `categoryId`), "
                    + "FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , "
                    + "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_categories_categoryId` ON `item_categories` (`categoryId`)");
            // Items that had a folder keep it; everything else goes into the Default folder.
            db.execSQL("INSERT OR IGNORE INTO `item_categories` (`itemId`, `categoryId`) "
                    + "SELECT `id`, `categoryId` FROM `items` WHERE `categoryId` IN (SELECT `id` FROM `categories`)");
            db.execSQL(PUT_LOOSE_ITEMS_IN_DEFAULT);
            db.execSQL("UPDATE `items` SET `categoryId` = NULL");
        }
    };

    /** Creates the Default folder if there isn't one (fresh install, or after a restore). */
    static final String INSERT_DEFAULT_IF_MISSING = "INSERT INTO `categories` (`name`, `position`, `isDefault`) "
            + "SELECT 'Default', 0, 1 WHERE NOT EXISTS (SELECT 1 FROM `categories` WHERE `isDefault` = 1)";

    /** Any item that is in no folder is put in the Default folder. */
    static final String PUT_LOOSE_ITEMS_IN_DEFAULT = "INSERT OR IGNORE INTO `item_categories` (`itemId`, `categoryId`) "
            + "SELECT `id`, (SELECT `id` FROM `categories` WHERE `isDefault` = 1 ORDER BY `id` LIMIT 1) FROM `items` "
            + "WHERE `id` NOT IN (SELECT `itemId` FROM `item_categories`)";

    private static volatile AppDatabase instance;

    @NonNull
    public static AppDatabase get(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "stockkeep.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                            .addCallback(new Callback() {
                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    // Safety net: there must always be a Default folder holding unfiled items.
                                    db.execSQL(INSERT_DEFAULT_IF_MISSING);
                                    db.execSQL(PUT_LOOSE_ITEMS_IN_DEFAULT);
                                }
                            })
                            .build();
                }
            }
        }
        return instance;
    }
}
