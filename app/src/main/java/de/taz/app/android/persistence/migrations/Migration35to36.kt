package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration35to36 : Migration(35, 36) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL(
                "CREATE TABLE IF NOT EXISTS `Playlist` (`audioPlayerItemId` TEXT NOT NULL, `audioFileName` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `uiTitle` TEXT NOT NULL, `uiAuthor` TEXT, `uiCoverImageUri` TEXT, `uiCoverImageGlidePath` TEXT, `uiOpenItemSpecDisplayableKey` TEXT, `issueDate` TEXT, `issueFeedName` TEXT, `issueStatus` TEXT, `playableKey` TEXT, `audioPlayerItemType` TEXT NOT NULL, PRIMARY KEY(`audioPlayerItemId`), FOREIGN KEY(`audioFileName`) REFERENCES `Audio`(`fileName`) ON UPDATE NO ACTION ON DELETE NO ACTION )".trimMargin())
            execSQL("CREATE INDEX `index_Playlist_audioFileName` ON `Playlist` (`audioFileName`)")
        }
    }
}