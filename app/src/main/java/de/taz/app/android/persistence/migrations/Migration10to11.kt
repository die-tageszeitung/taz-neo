package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration10to11 : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("CREATE INDEX IF NOT EXISTS `index_SectionNavButtonJoin_sectionFileName` ON SectionNavButtonJoin (`sectionFileName`)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_SectionNavButtonJoin_navButtonFileName` ON SectionNavButtonJoin (`navButtonFileName`)")

            execSQL("ALTER TABLE IssueMomentJoin RENAME TO IssueImageMomentJoin")
            execSQL("DROP TABLE IF EXISTS index_IssueMomentJoin_momentFileName")
            execSQL("CREATE INDEX IF NOT EXISTS `index_IssueImageMomentJoin_momentFileName` ON IssueImageMomentJoin (`momentFileName`)")

            execSQL("CREATE TABLE IF NOT EXISTS IssueCreditMomentJoin (`issueFeedName` TEXT NOT NULL, `issueDate` TEXT NOT NULL, `issueStatus` TEXT NOT NULL, `momentFileName` TEXT NOT NULL, `index` INTEGER NOT NULL, PRIMARY KEY(`issueFeedName`, `issueDate`, `issueStatus`, `momentFileName`), FOREIGN KEY(`issueFeedName`, `issueDate`, `issueStatus`) REFERENCES `Issue`(`feedName`, `date`, `status`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`momentFileName`) REFERENCES `FileEntry`(`name`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            execSQL("CREATE INDEX IF NOT EXISTS `index_IssueCreditMomentJoin_momentFileName` ON IssueCreditMomentJoin (`momentFileName`)")

            execSQL( "CREATE TABLE IF NOT EXISTS Moment (`issueFeedName` TEXT NOT NULL, `issueDate` TEXT NOT NULL, `issueStatus` TEXT NOT NULL, `downloadedStatus` TEXT, PRIMARY KEY(`issueFeedName`, `issueDate`, `issueStatus`))")
        }
    }
}