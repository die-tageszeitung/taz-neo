package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration26to27 : Migration(26, 27) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE Article ADD COLUMN hasAudio INTEGER NOT NULL DEFAULT 0;")
            execSQL("ALTER TABLE Article ADD COLUMN mediaSyncId INTEGER;")
            execSQL("ALTER TABLE Article ADD COLUMN chars INTEGER;")
            execSQL("ALTER TABLE Article ADD COLUMN words INTEGER;")
            execSQL("ALTER TABLE Article ADD COLUMN readMinutes INTEGER;")
        }
    }
}