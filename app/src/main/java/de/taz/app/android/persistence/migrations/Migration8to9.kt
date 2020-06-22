package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration8to9 : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            ALTER TABLE Article ADD COLUMN downloaded INTEGER DEFAULT NULL
            """
        )
        database.execSQL(
            """
            ALTER TABLE Section ADD COLUMN downloaded INTEGER DEFAULT NULL
            """
        )
        database.execSQL(
            """
            ALTER TABLE Issue ADD COLUMN downloaded INTEGER DEFAULT NULL
            """
        )
        database.execSQL(
            """
            ALTER TABLE Page ADD COLUMN downloaded INTEGER DEFAULT NULL
            """
        )
        database.execSQL(
            """
            ALTER TABLE ResourceInfo ADD COLUMN downloaded INTEGER DEFAULT NULL
            """
        )
    }
}