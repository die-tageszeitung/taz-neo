package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration37to38 : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Page` ADD COLUMN `adIdList` TEXT;")
        }
    }
}