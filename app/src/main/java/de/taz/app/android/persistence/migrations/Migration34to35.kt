package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration34to35 : Migration(34, 35) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE `FileEntry` RENAME TO `BKPFileEntry`")
            execSQL("CREATE TABLE IF NOT EXISTS `FileEntry` (`name` TEXT NOT NULL, `storageType` TEXT NOT NULL, `moTime` INTEGER NOT NULL, `sha256` TEXT NOT NULL, `size` INTEGER NOT NULL, `dateDownload` TEXT, `path` TEXT NOT NULL DEFAULT '', `storageLocation` TEXT NOT NULL, PRIMARY KEY(`name`))")
            execSQL("INSERT INTO `FileEntry` SELECT `name`, `storageType`, `moTime`, `sha256`, `size`, `dateDownload`, `path`, `storageLocation` FROM `BKPFileEntry`")
            execSQL("DROP TABLE `BKPFileEntry`")
        }
    }
}