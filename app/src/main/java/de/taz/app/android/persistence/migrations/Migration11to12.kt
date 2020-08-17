package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration11to12 : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL( "ALTER TABLE Issue ADD COLUMN moTime TEXT DEFAULT \"2020-08-17\";")
        }
    }
}