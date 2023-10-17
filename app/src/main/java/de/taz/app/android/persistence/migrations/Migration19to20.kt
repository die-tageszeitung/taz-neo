package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration19to20 : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE Page ADD COLUMN baseUrl TEXT NOT NULL DEFAULT '';")
            execSQL(
                """
                UPDATE Page
                SET baseUrl = (
                    SELECT baseUrl FROM Issue 
                        INNER JOIN IssuePageJoin 
                            ON IssuePageJoin.issueFeedName = Issue.feedName AND
                                IssuePageJoin.issueDate = Issue.date AND
                                IssuePageJoin.issueStatus = Issue.status AND
                                IssuePageJoin.pageKey = Page.pdfFileName
                    )
                
            """
            )
            // drop foreign key to issue:
            execSQL("CREATE TABLE IssuePageJoinBackup (`issueFeedName` TEXT NOT NULL, `issueDate` TEXT NOT NULL, `issueStatus` TEXT NOT NULL, `pageKey` TEXT NOT NULL, `index` INTEGER NOT NULL, PRIMARY KEY(`issueFeedName`, `issueDate`, `issueStatus`, `pageKey`), FOREIGN KEY(`pageKey`) REFERENCES `Page`(`pdfFileName`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            execSQL("INSERT INTO IssuePageJoinBackup SELECT * FROM IssuePageJoin;")
            execSQL("DROP TABLE IssuePageJoin;")
            execSQL("ALTER TABLE IssuePageJoinBackup RENAME TO IssuePageJoin;")
            execSQL("CREATE INDEX IF NOT EXISTS `index_IssuePageJoin_pageKey` ON IssuePageJoin (`pageKey`)")
        }
    }
}