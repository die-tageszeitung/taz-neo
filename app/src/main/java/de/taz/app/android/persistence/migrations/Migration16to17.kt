package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration16to17 : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL(
                """
                        ALTER TABLE Issue ADD COLUMN lastDisplayableName TEXT;
                    """
            )
            execSQL(
                """
                        CREATE TABLE ViewerState (
                            `displayableName` TEXT NOT NULL,
                            `scrollPosition` INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY (`displayableName`)
                        )
                    """
            )
        }
    }
}