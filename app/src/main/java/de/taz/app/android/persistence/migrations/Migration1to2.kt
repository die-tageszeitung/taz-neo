package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration1to2: Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE AppInfo ADD COLUMN androidVersion INTEGER NOT NULL DEFAULT 0"
        )
    }
}
