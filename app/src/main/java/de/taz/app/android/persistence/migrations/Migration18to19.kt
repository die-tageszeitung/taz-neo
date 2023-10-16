package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.taz.app.android.api.interfaces.StorageLocation

class Migration18to19 : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("""
                ALTER TABLE FileEntry ADD COLUMN path TEXT NOT NULL DEFAULT '';
            """)
            execSQL("""
                ALTER TABLE FileEntry ADD COLUMN storageLocation TEXT NOT NULL DEFAULT ${StorageLocation.NOT_STORED.name};
            """)
            execSQL("""
                UPDATE FileEntry
                SET path = printf("%s/%s", folder, name)
            """)
        }
    }
}