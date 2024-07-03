package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration33to34 : Migration(33, 34) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            // Add Image ForeignKey on SectionImageJoin
            execSQL("ALTER TABLE `SectionImageJoin` RENAME TO `OLDSectionImageJoin`")
            execSQL("CREATE TABLE IF NOT EXISTS `SectionImageJoin` (`sectionFileName` TEXT NOT NULL, `imageFileName` TEXT NOT NULL, `index` INTEGER NOT NULL, PRIMARY KEY(`sectionFileName`, `imageFileName`), FOREIGN KEY(`sectionFileName`) REFERENCES `Section`(`sectionFileName`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`imageFileName`) REFERENCES `Image`(`fileEntryName`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            execSQL("INSERT INTO `SectionImageJoin` (`sectionFileName`, `imageFileName`, `index`) SELECT `sectionFileName`, `imageFileName`, `index` FROM `OLDSectionImageJoin`")
            execSQL("DROP INDEX IF EXISTS `index_SectionImageJoin_imageFileName`")
            execSQL("CREATE INDEX IF NOT EXISTS `index_SectionImageJoin_imageFileName` ON `SectionImageJoin` (`imageFileName`)")
            execSQL("DROP TABLE `OLDSectionImageJoin`")

            // Add Image ForeignKey on ArticleImageJoin
            execSQL("ALTER TABLE `ArticleImageJoin` RENAME TO `OLDArticleImageJoin`")
            execSQL("CREATE TABLE IF NOT EXISTS `ArticleImageJoin` (`articleFileName` TEXT NOT NULL, `imageFileName` TEXT NOT NULL, `index` INTEGER NOT NULL, PRIMARY KEY(`articleFileName`, `imageFileName`), FOREIGN KEY(`articleFileName`) REFERENCES `Article`(`articleFileName`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`imageFileName`) REFERENCES `Image`(`fileEntryName`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            execSQL("INSERT INTO `ArticleImageJoin` (`articleFileName`, `imageFileName`, `index`) SELECT `articleFileName`, `imageFileName`, `index` FROM `OLDArticleImageJoin`")
            execSQL("DROP INDEX IF EXISTS `index_ArticleImageJoin_imageFileName`")
            execSQL("CREATE INDEX IF NOT EXISTS `index_ArticleImageJoin_imageFileName` ON `ArticleImageJoin` (`imageFileName`)")
            execSQL("DROP TABLE `OLDArticleImageJoin`")

            // Add Image ForeignKey on MomentCreditJoin
            execSQL("ALTER TABLE `MomentCreditJoin` RENAME TO `OLDMomentCreditJoin`")
            execSQL("CREATE TABLE IF NOT EXISTS `MomentCreditJoin` (`issueFeedName` TEXT NOT NULL, `issueDate` TEXT NOT NULL, `issueStatus` TEXT NOT NULL, `momentFileName` TEXT NOT NULL, `index` INTEGER NOT NULL, PRIMARY KEY(`issueFeedName`, `issueDate`, `issueStatus`, `momentFileName`), FOREIGN KEY(`issueFeedName`, `issueDate`, `issueStatus`) REFERENCES `Moment`(`issueFeedName`, `issueDate`, `issueStatus`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`momentFileName`) REFERENCES `Image`(`fileEntryName`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            execSQL("INSERT INTO `MomentCreditJoin` (`issueFeedName`, `issueDate`, `issueStatus`, `momentFileName`, `index`) SELECT `issueFeedName`, `issueDate`, `issueStatus`, `momentFileName`, `index` FROM `OLDMomentCreditJoin`")
            execSQL("DROP INDEX IF EXISTS `index_MomentCreditJoin_momentFileName`")
            execSQL("CREATE INDEX IF NOT EXISTS `index_MomentCreditJoin_momentFileName` ON `MomentCreditJoin` (`momentFileName`)")
            execSQL("DROP TABLE `OLDMomentCreditJoin`")

            // Add Image ForeignKey on MomentImageJoin
            execSQL("ALTER TABLE `MomentImageJoin` RENAME TO `OLDMomentImageJoin`")
            execSQL("CREATE TABLE IF NOT EXISTS `MomentImageJoin` (`issueFeedName` TEXT NOT NULL, `issueDate` TEXT NOT NULL, `issueStatus` TEXT NOT NULL, `momentFileName` TEXT NOT NULL, `index` INTEGER NOT NULL, PRIMARY KEY(`issueFeedName`, `issueDate`, `issueStatus`, `momentFileName`), FOREIGN KEY(`issueFeedName`, `issueDate`, `issueStatus`) REFERENCES `Moment`(`issueFeedName`, `issueDate`, `issueStatus`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`momentFileName`) REFERENCES `Image`(`fileEntryName`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            execSQL("INSERT INTO `MomentImageJoin` (`issueFeedName`, `issueDate`, `issueStatus`, `momentFileName`, `index`) SELECT `issueFeedName`, `issueDate`, `issueStatus`, `momentFileName`, `index` FROM `OLDMomentImageJoin`")
            execSQL("DROP INDEX IF EXISTS `index_MomentImageJoin_momentFileName`")
            execSQL("CREATE INDEX IF NOT EXISTS `index_MomentImageJoin_momentFileName` ON `MomentImageJoin` (`momentFileName`)")
            execSQL("DROP TABLE `OLDMomentImageJoin`")
        }
    }
}