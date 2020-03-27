package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.taz.app.android.persistence.repository.IssueRepository

object Migration6to7 : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            INSERT INTO Image (
                name, storageType, moTime, sha256, size, folder, type, alpha, resolution
            ) VALUES (
                'navButton.taz.normal.png', 'resource', 1564992869, 
                'c9efd6fc5ca392a9d417cff4990130e533fadb6ce4f81a9577c50e7252842d2a', 6286,
                'resources', 'button', 1, 'normal'
            )""".trimMargin()
        )

        database.execSQL("""
            Insert SectionNavButtonJoin(sectionFileName, navButtonFileName, navButtonStorageType)
            Select sectionFileName, 'navButton.taz.normal.png', 'resource' 
            From Section 
            """.trimIndent()
        )

    }
}