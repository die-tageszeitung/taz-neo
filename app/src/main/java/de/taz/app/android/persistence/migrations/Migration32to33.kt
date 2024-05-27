package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


class Migration32to33 : Migration(32, 33) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE `Article` ADD COLUMN `pdfFileName` TEXT REFERENCES `FileEntry`(`name`) ON UPDATE NO ACTION ON DELETE NO ACTION")
            execSQL("CREATE INDEX IF NOT EXISTS `index_Article_pdfFileName` ON `Article` (`pdfFileName`)")
        }
    }
}