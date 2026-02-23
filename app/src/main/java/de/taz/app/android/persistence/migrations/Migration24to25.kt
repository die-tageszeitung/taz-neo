package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration24to25 : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE Issue ADD COLUMN validityDate TEXT;")
        }
    }
}