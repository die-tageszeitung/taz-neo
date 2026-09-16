package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration42to43 : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("CREATE TABLE IF NOT EXISTS `Podcasts` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `title` TEXT NOT NULL, `displayName` TEXT NOT NULL, `version` INTEGER NOT NULL, `useDefaultIcon` INTEGER NOT NULL, `defaultIconFileEntryName` TEXT, `episodeCnt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            execSQL("CREATE INDEX IF NOT EXISTS `index_Podcasts_id` ON `Podcasts` (`id`)")
            execSQL("CREATE TABLE IF NOT EXISTS `PodcastEpisodes` (`id` INTEGER NOT NULL, `podcastId` INTEGER NOT NULL, `pubTime` TEXT NOT NULL, `mediaSyncId` INTEGER NOT NULL, `title` TEXT, `teaser` TEXT, `headLine` TEXT, `authors` TEXT, `iconFileEntryName` TEXT, `audioFileName` TEXT, `transcriptionFileName` TEXT, `playtime` INTEGER, `duration` REAL, `alreadyPlayed` REAL NOT NULL DEFAULT 0.0, PRIMARY KEY(`id`), FOREIGN KEY(`podcastId`) REFERENCES `Podcasts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            execSQL("CREATE INDEX IF NOT EXISTS `index_PodcastEpisodes_podcastId` ON `PodcastEpisodes` (`podcastId`)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_PodcastEpisodes_id` ON `PodcastEpisodes` (`id`)")
        }
    }
}
