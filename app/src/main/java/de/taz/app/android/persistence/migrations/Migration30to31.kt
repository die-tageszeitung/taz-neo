package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration30to31 : Migration(30, 31) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE ViewerState ADD COLUMN scrollPositionHorizontal INTEGER NOT NULL DEFAULT 0;")
        }
    }
}