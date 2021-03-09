package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration19to20 : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("""
                ALTER TABLE Page ADD COLUMN baseUrl TEXT NOT NULL DEFAULT '';
            """)
        }
    }
}