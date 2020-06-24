package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration9to10 : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {

            execSQL("""CREATE TABLE TmpArticle (`articleFileName` TEXT NOT NULL, `issueFeedName` TEXT NOT NULL, `issueDate` TEXT NOT NULL, `title` TEXT, `teaser` TEXT, `onlineLink` TEXT, `pageNameList` TEXT NOT NULL, `bookmarked` INTEGER NOT NULL, `articleType` TEXT NOT NULL, `position` INTEGER NOT NULL, `percentage` INTEGER NOT NULL, `downloadedStatus` TEXT, PRIMARY KEY(`articleFileName`))""")
            execSQL(""" INSERT INTO TmpArticle (articleFileName, issueFeedName, issueDate, title, teaser, onlineLink, pageNameList, bookmarked, articleType, position, percentage) SELECT articleFileName, issueFeedName, issueDate, title, teaser, onlineLink, pageNameList, bookmarked, articleType, position, percentage FROM Article """)
            execSQL("""DROP TABLE Article""")
            execSQL("""ALTER TABLE TmpArticle RENAME TO Article""")

            execSQL("""CREATE TABLE TmpSection (`sectionFileName` TEXT NOT NULL, `issueDate` TEXT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, `extendedTitle` TEXT, `downloadedStatus` TEXT, PRIMARY KEY(`sectionFileName`))""")
            execSQL(""" INSERT INTO TmpSection (sectionFileName, issueDate, title, type, extendedTitle) SELECT sectionFileName, issueDate, title, type, extendedTitle FROM Section""")
            execSQL("""DROP TABLE Section""")
            execSQL("""ALTER TABLE TmpSection RENAME TO Section""")

            execSQL("""CREATE TABLE TmpIssue (`feedName` TEXT NOT NULL, `date` TEXT NOT NULL, `key` TEXT, `baseUrl` TEXT NOT NULL, `status` TEXT NOT NULL, `minResourceVersion` INTEGER NOT NULL, `isWeekend` INTEGER NOT NULL DEFAULT 0, `dateDownload` TEXT, `downloadedStatus` TEXT, PRIMARY KEY(`feedName`, `date`, `status`))""")
            execSQL(""" INSERT INTO TmpIssue (feedName, date, `key`, baseUrl, status, minResourceVersion, isWeekend, dateDownload) SELECT feedName, date, `key`, baseUrl, status, minResourceVersion, isWeekend, dateDownload FROM Issue""")
            execSQL("""DROP TABLE Issue """)
            execSQL("""ALTER TABLE TmpIssue RENAME TO Issue """)

            execSQL("""CREATE TABLE TmpPage (`pdfFileName` TEXT NOT NULL, `title` TEXT, `pagina` TEXT, `type` TEXT, `frameList` TEXT, `downloadedStatus` TEXT, PRIMARY KEY(`pdfFileName`))""")
            execSQL(""" INSERT INTO TmpPage (pdfFileName, title, pagina, type, frameList) SELECT pdfFileName, title, pagina, type, frameList FROM Page""")
            execSQL("""DROP TABLE Page""")
            execSQL("""ALTER TABLE TmpPage RENAME TO Page""")

            execSQL("""CREATE TABLE TmpResourceInfo (`resourceVersion` INTEGER NOT NULL, `resourceBaseUrl` TEXT NOT NULL, `resourceZip` TEXT NOT NULL, `downloadedStatus` TEXT, PRIMARY KEY(`resourceVersion`))""")
            execSQL(""" INSERT INTO TmpResourceInfo (resourceVersion, resourceBaseUrl, resourceZip) SELECT resourceVersion, resourceBaseUrl, resourceZip FROM ResourceInfo""")
            execSQL("""DROP TABLE ResourceInfo """)
            execSQL("""ALTER TABLE TmpResourceInfo RENAME TO ResourceInfo""")

            execSQL("""CREATE TABLE TmpFileEntry  (`name` TEXT NOT NULL, `storageType` TEXT NOT NULL, `moTime` INTEGER NOT NULL, `sha256` TEXT NOT NULL, `size` INTEGER NOT NULL, `folder` TEXT NOT NULL, `downloadedStatus` TEXT, PRIMARY KEY(`name`))""")
            execSQL(""" INSERT INTO TmpFileEntry (name, storageType, moTime, sha256, size, folder) SELECT name, storageType, moTime, sha256, size, folder FROM FileEntry""")
            execSQL("""DROP TABLE FileEntry """)
            execSQL("""ALTER TABLE TmpFileEntry RENAME TO FileEntry """)


            execSQL("""CREATE INDEX IF NOT EXISTS `index_SectionNavButtonJoin_sectionFileName` ON `SectionNavButtonJoin` (`sectionFileName`)""")
        }


    }
}