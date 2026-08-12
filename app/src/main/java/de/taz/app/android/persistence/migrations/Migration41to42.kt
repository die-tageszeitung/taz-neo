package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration41to42 : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.apply {
            // SQLite version in Android < 14 does not support 'ALTER TABLE DROP COLUMN'.
            // We must use the 'temporary table' strategy instead.

            // 1. Create a new table with the desired schema
            execSQL("CREATE TABLE IF NOT EXISTS `Page_new` (`pdfFileName` TEXT NOT NULL, `title` TEXT, `pagina` TEXT, `type` TEXT, `frameList` TEXT, `baseUrl` TEXT NOT NULL, `podcastFileName` TEXT, `adIdList` TEXT, PRIMARY KEY(`pdfFileName`), FOREIGN KEY(`podcastFileName`) REFERENCES `Audio`(`fileName`) ON UPDATE NO ACTION ON DELETE NO ACTION )")

            // 2. Copy the data from the old table to the new one
            execSQL("INSERT INTO `Page_new` (`pdfFileName`, `title`, `pagina`, `type`, `frameList`, `baseUrl`, `podcastFileName`, `adIdList`) SELECT `pdfFileName`, `title`, `pagina`, `type`, `frameList`, `baseUrl`, `podcastFileName`, `adIdList` FROM `Page`")

            // 3. Drop the old table
            execSQL("DROP TABLE `Page`")

            // 4. Rename the new table to the original name
            execSQL("ALTER TABLE `Page_new` RENAME TO `Page`")

            // 5. Re-create indices
            execSQL("CREATE INDEX IF NOT EXISTS `index_Page_podcastFileName` ON `Page` (`podcastFileName`)")
        }
    }
}