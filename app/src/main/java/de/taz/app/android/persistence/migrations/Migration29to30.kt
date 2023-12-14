package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration29to30 : Migration(29, 30) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("DROP TABLE `SectionNavButtonJoin`")
        }
    }
}