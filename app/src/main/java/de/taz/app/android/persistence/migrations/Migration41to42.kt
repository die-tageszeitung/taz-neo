package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration41to42 : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Page` DROP COLUMN `dateDownload`;")
        }
    }
}