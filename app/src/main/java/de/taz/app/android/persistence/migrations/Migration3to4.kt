package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration3to4 : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IssueBackup AS SELECT feedName, date, "key", baseUrl, status, minResourceVersion, zipName, zipPdfName, fileList, fileListPdf, dateDownload FROM Issue"""
        )
        database.execSQL(
            "DROP TABLE Issue"
        )
        database.execSQL(
            "ALTER TABLE IssueBackup RENAME TO Issue"
        )
    }
}
