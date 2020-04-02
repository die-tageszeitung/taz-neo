package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.taz.app.android.persistence.repository.IssueRepository

object Migration6to7 : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE Image (`name` TEXT NOT NULL, `storageType` TEXT NOT NULL, `moTime` INTEGER NOT NULL, `sha256` TEXT NOT NULL, `size` INTEGER NOT NULL, `folder` TEXT NOT NULL, `type` TEXT NOT NULL, `alpha` REAL NOT NULL, `resolution` TEXT NOT NULL, PRIMARY KEY(`name`, `storageType`))")

        database.execSQL("CREATE TABLE SectionNavButtonJoin  (`sectionFileName` TEXT NOT NULL, `navButtonFileName` TEXT NOT NULL, `navButtonStorageType` TEXT NOT NULL, PRIMARY KEY(`sectionFileName`, `navButtonFileName`, `navButtonStorageType`), FOREIGN KEY(`sectionFileName`) REFERENCES `Section`(`sectionFileName`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`navButtonFileName`, `navButtonStorageType`) REFERENCES `Image`(`name`, `storageType`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_SectionNavButtonJoin_sectionFileName` ON SectionNavButtonJoin (`sectionFileName`)")

        database.execSQL(
            """
            INSERT INTO Image (
                name, storageType, moTime, sha256, size, folder, type, alpha, resolution
            ) VALUES (
                'navButton.taz.normal.png', 'resource', 1564992869, 
                'c9efd6fc5ca392a9d417cff4990130e533fadb6ce4f81a9577c50e7252842d2a', 6286,
                'resources', 'button', 1, 'normal'
            )""".trimMargin()
        )

        database.execSQL(
            """
            INSERT INTO SectionNavButtonJoin(
                sectionFileName, navButtonFileName, navButtonStorageType
            ) SELECT sectionFileName, 'navButton.taz.normal.png', 'resource'
            From Section 
            """.trimIndent()
        )

    }
}