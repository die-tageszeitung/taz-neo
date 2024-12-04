package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration36to37 : Migration(36, 37) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("""
                CREATE TABLE IF NOT EXISTS `BookmarkSynchronization`(
                    `mediaSyncId` INTEGER NOT NULL, 
                    `articleDate` TEXT NOT NULL,
                    `from` TEXT NOT NULL,
                    `locallyChangedTime` TEXT,
                    `synchronizedTime` TEXT, 
                    PRIMARY KEY(`mediaSyncId`)
                )""".trimIndent())
        }
    }
}