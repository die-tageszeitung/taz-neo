package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration19to20 : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE Page ADD COLUMN baseUrl TEXT NOT NULL DEFAULT '';")
        // drop foreign key to issue:
        database.execSQL("CREATE TABLE IssuePageJoinBackup (`issueFeedName` TEXT NOT NULL, `issueDate` TEXT NOT NULL, `issueStatus` TEXT NOT NULL, `pageKey` TEXT NOT NULL, `index` INTEGER NOT NULL, PRIMARY KEY(`issueFeedName`, `issueDate`, `issueStatus`, `pageKey`), FOREIGN KEY(`pageKey`) REFERENCES `Page`(`pdfFileName`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
        database.execSQL("INSERT INTO IssuePageJoinBackup SELECT * FROM IssuePageJoin;")
        database.execSQL("DROP TABLE IssuePageJoin;")
        database.execSQL("ALTER TABLE IssuePageJoinBackup RENAME TO IssuePageJoin;")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_IssuePageJoin_pageKey` ON IssuePageJoin (`pageKey`)")
    }
}