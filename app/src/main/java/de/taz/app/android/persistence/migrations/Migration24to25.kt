package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration24to25 : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE Article ADD COLUMN hasAudio INTEGER NOT NULL DEFAULT 0;")
        }
    }
}