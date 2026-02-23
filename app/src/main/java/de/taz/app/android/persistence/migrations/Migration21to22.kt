package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration21to22 : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE Issue ADD COLUMN lastPagePosition INT;")
        }
    }
}