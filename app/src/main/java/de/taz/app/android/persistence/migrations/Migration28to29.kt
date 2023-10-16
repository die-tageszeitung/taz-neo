package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration28to29 : Migration(28, 29) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE `Section` ADD COLUMN `podcastFileName` TEXT REFERENCES `Audio`(`fileName`) ON UPDATE NO ACTION ON DELETE NO ACTION")
            execSQL("CREATE INDEX IF NOT EXISTS `index_Section_podcastFileName` ON `Section` (`podcastFileName`)")
        }
    }
}