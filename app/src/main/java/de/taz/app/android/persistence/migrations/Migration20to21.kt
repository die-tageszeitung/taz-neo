package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration20to21 : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE Issue ADD COLUMN dateDownloadWithPages TEXT;")
        }
    }
}