package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration23to24 : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE Article ADD COLUMN bookmarkedTime TEXT;")
            execSQL("UPDATE Article SET bookmarkedTime = DATETIME('now','localtime') WHERE bookmarked != 0;")
            execSQL(
                """CREATE TABLE Article_temp (
                    `articleFileName` TEXT NOT NULL,
                    `issueFeedName` TEXT NOT NULL,
                    `issueDate` TEXT NOT NULL,
                    `title` TEXT,
                    `teaser` TEXT,
                    `onlineLink` TEXT,
                    `pageNameList` TEXT NOT NULL,
                    `bookmarkedTime` TEXT,
                    `articleType` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `percentage` INTEGER NOT NULL,
                    `dateDownload` TEXT,
                    PRIMARY KEY(`articleFileName`)
                );""".trimMargin())
            execSQL(
                """INSERT INTO Article_temp (articleFileName, issueFeedName, issueDate, title, teaser, onlineLink, pageNameList, bookmarkedTime, articleType, position, percentage, dateDownload)
                SELECT 
                    articleFileName,
                    issueFeedName,
                    issueDate,
                    title,
                    teaser,
                    onlineLink,
                    pageNameList,
                    bookmarkedTime,
                    articleType,
                    position,
                    percentage,
                    dateDownload
                FROM Article;""".trimMargin())
            execSQL("DROP TABLE Article;")
            execSQL("ALTER TABLE Article_temp RENAME TO Article;")
        }
    }
}