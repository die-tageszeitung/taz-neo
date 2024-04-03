package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration31to32 : Migration(31, 32) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE `ArticleAuthor` RENAME TO `OLDArticleAuthor`")

            execSQL("CREATE TABLE IF NOT EXISTS `ArticleAuthor` (`articleFileName` TEXT NOT NULL, `authorName` TEXT, `authorFileName` TEXT, `index` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT, FOREIGN KEY(`articleFileName`) REFERENCES `Article`(`articleFileName`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`authorFileName`) REFERENCES `FileEntry`(`name`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            execSQL("DROP INDEX IF EXISTS `index_ArticleAuthor_authorFileName`")
            execSQL("CREATE INDEX `index_ArticleAuthor_authorFileName` ON `ArticleAuthor` (`authorFileName`)")
            execSQL("DROP INDEX IF EXISTS `index_ArticleAuthor_articleFileName`")
            execSQL("CREATE INDEX `index_ArticleAuthor_articleFileName` ON `ArticleAuthor` (`articleFileName`)")

            execSQL("INSERT INTO `ArticleAuthor` (`articleFileName`, `authorName`, `authorFileName`, `index`) SELECT `articleFileName`, `authorName`, `authorFileName`, `index` FROM `OLDArticleAuthor`")
            execSQL("DROP TABLE `OLDArticleAuthor`")

            execSQL("ALTER TABLE `Image` RENAME TO `OLDImage`")

            execSQL("CREATE TABLE IF NOT EXISTS `Image` (`fileEntryName` TEXT NOT NULL, `type` TEXT NOT NULL, `alpha` REAL NOT NULL, `resolution` TEXT NOT NULL, PRIMARY KEY(`fileEntryName`), FOREIGN KEY(`fileEntryName`) REFERENCES `FileEntry`(`name`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            execSQL("DROP INDEX IF EXISTS `index_Image_fileEntryName`")
            execSQL("CREATE INDEX `index_Image_fileEntryName` ON `Image` (`fileEntryName`)")

            execSQL("INSERT INTO `Image` (`fileEntryName`, `type`, `alpha`, `resolution`) SELECT `fileEntryName`, `type`, `alpha`, `resolution` FROM `OLDImage`")
            execSQL("DROP TABLE `OLDImage`")
        }
    }
}