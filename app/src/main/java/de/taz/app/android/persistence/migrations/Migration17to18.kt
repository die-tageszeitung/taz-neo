package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration17to18 : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            // Rename IssueCreditMomentJoin To MomentCreditJoin as it drops FK to Issue and uses a FK to Moment instead
            execSQL("""
                CREATE TABLE MomentCreditJoin(
                    `issueFeedName` TEXT NOT NULL,
                    `issueDate` TEXT NOT NULL,
                    `issueStatus` TEXT NOT NULL,
                    `momentFileName` TEXT NOT NULL,
                    `index` INTEGER NOT NULL,
                    PRIMARY KEY(`issueFeedName`, `issueDate`, `issueStatus`, `momentFileName`),
                    FOREIGN KEY(`momentFileName`) REFERENCES `FileEntry`(`name`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                    FOREIGN KEY(`issueFeedName`, `issueDate`, `issueStatus`) REFERENCES `Moment`(`issueFeedName`, `issueDate`, `issueStatus`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_MomentCreditJoin_momentFileName` ON MomentCreditJoin (`momentFileName`)")

            execSQL("""
                INSERT INTO MomentCreditJoin(
                    issueFeedName,
                    issueDate,
                    issueStatus,
                    momentFileName,
                    `index`
                ) SELECT                     
                    issueFeedName,
                    issueDate,
                    issueStatus,
                    momentFileName,
                    `index` FROM IssueCreditMomentJoin
                """)
            execSQL("""DROP TABLE IssueCreditMomentJoin""")

            // Rename IssueImageMomentJoin To MomentImageJoin as it drops FK to Issue and uses a FK to Moment instead
            execSQL("""
                CREATE TABLE MomentImageJoin(
                    `issueFeedName` TEXT NOT NULL,
                    `issueDate` TEXT NOT NULL,
                    `issueStatus` TEXT NOT NULL,
                    `momentFileName` TEXT NOT NULL,
                    `index` INTEGER NOT NULL,
                    PRIMARY KEY(`issueFeedName`, `issueDate`, `issueStatus`, `momentFileName`),
                    FOREIGN KEY(`momentFileName`) REFERENCES `FileEntry`(`name`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                    FOREIGN KEY(`issueFeedName`, `issueDate`, `issueStatus`) REFERENCES `Moment`(`issueFeedName`, `issueDate`, `issueStatus`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_MomentImageJoin_momentFileName` ON MomentImageJoin (`momentFileName`)")

            execSQL("""
                INSERT INTO MomentImageJoin(
                    issueFeedName,
                    issueDate,
                    issueStatus,
                    momentFileName,
                    `index`
                ) SELECT                     
                    issueFeedName,
                    issueDate,
                    issueStatus,
                    momentFileName,
                    `index` FROM IssueImageMomentJoin
                """)
            execSQL("""DROP TABLE IssueImageMomentJoin""")

            // Rename IssueFilesMomentJoin To MomentFilesJoin as it drops FK to Issue and uses a FK to Moment instead
            execSQL("""
                CREATE TABLE MomentFilesJoin(
                    `issueFeedName` TEXT NOT NULL,
                    `issueDate` TEXT NOT NULL,
                    `issueStatus` TEXT NOT NULL,
                    `momentFileName` TEXT NOT NULL,
                    `index` INTEGER NOT NULL,
                    PRIMARY KEY(`issueFeedName`, `issueDate`, `issueStatus`, `momentFileName`),
                    FOREIGN KEY(`momentFileName`) REFERENCES `FileEntry`(`name`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                    FOREIGN KEY(`issueFeedName`, `issueDate`, `issueStatus`) REFERENCES `Moment`(`issueFeedName`, `issueDate`, `issueStatus`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_MomentFilesJoin_momentFileName` ON MomentFilesJoin (`momentFileName`)")

            execSQL("""
                INSERT INTO MomentFilesJoin(
                    issueFeedName,
                    issueDate,
                    issueStatus,
                    momentFileName,
                    `index`
                ) SELECT                     
                    issueFeedName,
                    issueDate,
                    issueStatus,
                    momentFileName,
                    `index` FROM IssueFilesMomentJoin
                """)
            execSQL("""DROP TABLE IssueFilesMomentJoin""")

            execSQL("ALTER TABLE Moment ADD COLUMN baseUrl TEXT NOT NULL DEFAULT \"\"")
            execSQL("""
                UPDATE Moment
                SET baseUrl = (SELECT baseUrl FROM Issue WHERE Issue.feedName = Moment.issueFeedName AND Issue.date = Moment.issueDate AND Issue.status = Moment.issueStatus)
                
            """)
        }
    }
}