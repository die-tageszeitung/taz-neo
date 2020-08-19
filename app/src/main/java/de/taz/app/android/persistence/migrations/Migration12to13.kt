package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration12to13 : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("CREATE TABLE IF NOT EXISTS TMPDownload (`fileName` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `status` TEXT NOT NULL, `lastSha256` TEXT, PRIMARY KEY(`fileName`))")
            execSQL("INSERT INTO TMPDownload (fileName, baseUrl, status, lastSha256) SELECT fileName, baseUrl, status, lastSha256 FROM Download")
            database.execSQL("DROP TABLE Download")
            database.execSQL("ALTER TABLE TMPDownload RENAME TO Download")
        }
    }
}