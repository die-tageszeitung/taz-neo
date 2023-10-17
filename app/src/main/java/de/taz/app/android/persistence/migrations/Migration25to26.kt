package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Drop all the feeds as we have a new format for the publicationDates
// The feed will be re-loaded on app start
class Migration25to26 : Migration(25, 26) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("DELETE FROM Feed;")
        }
    }
}