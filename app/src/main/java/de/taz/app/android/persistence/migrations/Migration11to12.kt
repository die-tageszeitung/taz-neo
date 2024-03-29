package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration11to12 : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL( "ALTER TABLE Issue ADD COLUMN moTime TEXT NOT NULL DEFAULT \"2020-08-17\";")
        }
    }
}