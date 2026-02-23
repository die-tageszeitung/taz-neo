package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration12to13 : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("CREATE TABLE IF NOT EXISTS TMPDownload (`fileName` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `status` TEXT NOT NULL, `lastSha256` TEXT, PRIMARY KEY(`fileName`))")
            execSQL("INSERT INTO TMPDownload (fileName, baseUrl, status, lastSha256) SELECT fileName, baseUrl, status, lastSha256 FROM Download")
            db.execSQL("DROP TABLE Download")
            db.execSQL("ALTER TABLE TMPDownload RENAME TO Download")
        }
    }
}