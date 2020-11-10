package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration15to16 : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL( "ALTER TABLE Feed ADD COLUMN publicationDates TEXT NOT NULL DEFAULT \"[]\";")
        }
    }
}