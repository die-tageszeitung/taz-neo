package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration3to4 : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IssueBackup (`feedName` TEXT NOT NULL, `date` TEXT NOT NULL, `key` TEXT, `baseUrl` TEXT NOT NULL, `status` TEXT NOT NULL, `minResourceVersion` INTEGER NOT NULL, `dateDownload` TEXT, PRIMARY KEY(`feedName`, `date`, `status`))")
        database.execSQL("INSERT INTO IssueBackup SELECT feedName, date, \"key\", baseUrl, status, minResourceVersion, dateDownload FROM Issue;")
        database.execSQL("DROP TABLE Issue;")
        database.execSQL("ALTER TABLE IssueBackup RENAME TO Issue;")
    }
}
