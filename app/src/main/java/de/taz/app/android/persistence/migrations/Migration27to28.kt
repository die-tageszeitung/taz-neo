package de.taz.app.android.persistence.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.taz.app.android.api.models.AudioSpeaker
import de.taz.app.android.persistence.typeconverters.AudioSpeakerConverter

class Migration27to28 : Migration(27, 28) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("CREATE TABLE IF NOT EXISTS `Audio` (`fileName` TEXT NOT NULL, `playtime` INTEGER, `duration` REAL, `speaker` TEXT NOT NULL, `breaks` TEXT, PRIMARY KEY(`fileName`), FOREIGN KEY(`fileName`) REFERENCES `FileEntry`(`name`) ON UPDATE NO ACTION ON DELETE NO ACTION )")

            // Drop hasAudio field and add audioFileName field
            execSQL("CREATE TABLE IF NOT EXISTS `ArticleNew` (`articleFileName` TEXT NOT NULL, `issueFeedName` TEXT NOT NULL, `issueDate` TEXT NOT NULL, `title` TEXT, `teaser` TEXT, `onlineLink` TEXT, `pageNameList` TEXT NOT NULL, `bookmarkedTime` TEXT, `audioFileName` TEXT, `articleType` TEXT NOT NULL, `position` INTEGER NOT NULL, `percentage` INTEGER NOT NULL, `dateDownload` TEXT, `mediaSyncId` INTEGER, `chars` INTEGER, `words` INTEGER, `readMinutes` INTEGER, PRIMARY KEY(`articleFileName`), FOREIGN KEY(`audioFileName`) REFERENCES `Audio`(`fileName`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            execSQL(
                "INSERT INTO `ArticleNew` (`articleFileName`, `issueFeedName`, `issueDate`, `title`, `teaser`, `onlineLink`, `pageNameList`, `bookmarkedTime`, `articleType`, `position`, `percentage`, `dateDownload`, `mediaSyncId`, `chars`, `words`, `readMinutes`) " +
                        "  SELECT              `articleFileName`, `issueFeedName`, `issueDate`, `title`, `teaser`, `onlineLink`, `pageNameList`, `bookmarkedTime`, `articleType`, `position`, `percentage`, `dateDownload`, `mediaSyncId`, `chars`, `words`, `readMinutes` " +
                        "    FROM `Article`"
            )
            execSQL("DROP TABLE Article")
            execSQL("ALTER TABLE ArticleNew RENAME TO Article")
            execSQL("CREATE INDEX IF NOT EXISTS `index_Article_audioFileName` ON `Article` (`audioFileName`)")

            migrateData(database)

            execSQL("DROP TABLE ArticleAudioFileJoin")
        }
    }

    private fun migrateData(database: SupportSQLiteDatabase) {
        val audioSpeakerConverter = AudioSpeakerConverter()
        val values = ContentValues()
        database.apply {
            val c = query("SELECT articleFileName, audioFileName FROM ArticleAudioFileJoin")
            while (c.moveToNext()) {
                val articleFileName = c.getString(0)
                val audioFileName = c.getString(1)

                values.apply {
                    clear()
                    put("audioFileName", audioFileName)
                }
                database.update(
                    "Article",
                    SQLiteDatabase.CONFLICT_IGNORE,
                    values,
                    "articleFileName = ?",
                    arrayOf(articleFileName)
                )

                values.apply {
                    clear()
                    put("fileName", audioFileName)
                    put("speaker", audioSpeakerConverter.toString(AudioSpeaker.UNKNOWN))
                }
                database.insert("Audio", SQLiteDatabase.CONFLICT_IGNORE, values)
            }
            c.close()
        }
    }
}