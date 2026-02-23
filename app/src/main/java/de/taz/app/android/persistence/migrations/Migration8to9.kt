package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration8to9 : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE Article ADD COLUMN downloadedField INTEGER DEFAULT NULL
            """
        )
        db.execSQL(
            """
            ALTER TABLE Section ADD COLUMN downloadedField INTEGER DEFAULT NULL
            """
        )
        db.execSQL(
            """
            ALTER TABLE Issue ADD COLUMN downloadedField INTEGER DEFAULT NULL
            """
        )
        db.execSQL(
            """
            ALTER TABLE Page ADD COLUMN downloadedField INTEGER DEFAULT NULL
            """
        )
        db.execSQL(
            """
            ALTER TABLE ResourceInfo ADD COLUMN downloadedField INTEGER DEFAULT NULL
            """
        )
        db.execSQL(
            """
            ALTER TABLE FileEntry ADD COLUMN downloadedField INTEGER DEFAULT NULL
            """
        )
    }
}