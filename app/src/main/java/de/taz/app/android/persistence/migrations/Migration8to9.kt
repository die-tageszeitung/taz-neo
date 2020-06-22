package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration8to9 : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            ALTER TABLE Article ADD COLUMN downloadedField INTEGER DEFAULT NULL
            """
        )
        database.execSQL(
            """
            ALTER TABLE Section ADD COLUMN downloadedField INTEGER DEFAULT NULL
            """
        )
        database.execSQL(
            """
            ALTER TABLE Issue ADD COLUMN downloadedField INTEGER DEFAULT NULL
            """
        )
        database.execSQL(
            """
            ALTER TABLE Page ADD COLUMN downloadedField INTEGER DEFAULT NULL
            """
        )
        database.execSQL(
            """
            ALTER TABLE ResourceInfo ADD COLUMN downloadedField INTEGER DEFAULT NULL
            """
        )
        database.execSQL(
            """
            ALTER TABLE FileEntry ADD COLUMN downloadedField INTEGER DEFAULT NULL
            """
        )
    }
}