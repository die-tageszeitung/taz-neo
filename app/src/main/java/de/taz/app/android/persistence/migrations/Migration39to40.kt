package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration39to40 : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Issue` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 0;")
        }
    }
}