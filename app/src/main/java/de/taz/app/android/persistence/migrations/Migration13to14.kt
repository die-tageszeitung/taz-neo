package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration13to14 : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("CREATE TABLE IF NOT EXISTS IssueFilesMomentJoin (`issueFeedName` TEXT NOT NULL, `issueDate` TEXT NOT NULL, `issueStatus` TEXT NOT NULL, `momentFileName` TEXT NOT NULL, `index` INTEGER NOT NULL, PRIMARY KEY(`issueFeedName`, `issueDate`, `issueStatus`, `momentFileName`), FOREIGN KEY(`issueFeedName`, `issueDate`, `issueStatus`) REFERENCES `Issue`(`feedName`, `date`, `status`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`momentFileName`) REFERENCES `FileEntry`(`name`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            execSQL("CREATE INDEX IF NOT EXISTS `index_IssueFilesMomentJoin_momentFileName` ON IssueFilesMomentJoin (`momentFileName`)")
        }
    }
}