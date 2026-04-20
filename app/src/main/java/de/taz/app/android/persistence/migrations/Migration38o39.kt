package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration38to39 : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Feed` ADD COLUMN `displayName` TEXT NOT NULL DEFAULT \"\";")
        }
    }
}