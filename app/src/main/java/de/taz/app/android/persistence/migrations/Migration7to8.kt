package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration7to8 : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {

        database.execSQL("INSERT INTO FileEntry SELECT name, storageType, moTime, sha256, size, folder FROM Image")

        database.execSQL("CREATE TABLE ImageStub (`fileEntryName` TEXT NOT NULL, `type` TEXT NOT NULL, `alpha` REAL NOT NULL, `resolution` TEXT NOT NULL, PRIMARY KEY(`fileEntryName`))")
        database.execSQL("INSERT INTO ImageStub (fileEntryName, type, alpha, resolution) SELECT name, type, alpha, resolution FROM Image")

        database.execSQL("DROP TABLE Image")

        database.execSQL("ALTER TABLE ImageStub RENAME TO Image")

        database.execSQL("CREATE TABLE SNBJ2 (`sectionFileName` TEXT NOT NULL, `navButtonFileName` TEXT NOT NULL, PRIMARY KEY(`sectionFileName`, `navButtonFileName`), FOREIGN KEY(`sectionFileName`) REFERENCES `Section`(`sectionFileName`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`navButtonFileName`) REFERENCES `Image`(`fileEntryName`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
        database.execSQL("INSERT INTO SNBJ2 (sectionFileName, navButtonFileName) SELECT sectionFileName, navButtonFileName FROM SectionNavButtonJoin")
        database.execSQL("DROP TABLE SectionNavButtonJoin")
        database.execSQL("ALTER TABLE SNBJ2 RENAME TO SectionNavButtonJoin")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_SectionNavButtonJoin_sectionFileName` ON SectionNavButtonJoin(`sectionFileName`)")

        database.execSQL(
            """
            INSERT INTO Image (fileEntryName, type, alpha, resolution)
            SELECT FileEntry.name, 'picture', '1.0', 'small'
            From FileEntry WHERE FileEntry.name LIKE '%.small'
            """
        )

        database.execSQL(
            """
            INSERT INTO Image (fileEntryName, type, alpha, resolution)
            SELECT FileEntry.name, 'picture', '1.0', 'normal'
            From FileEntry WHERE FileEntry.name LIKE '%.norm'
            """
        )

        database.execSQL(
            """
            INSERT INTO Image (fileEntryName, type, alpha, resolution)
            SELECT FileEntry.name, 'picture', '1.0', 'normal'
            From FileEntry WHERE FileEntry.name LIKE '%.quadrat'
            """
        )
        database.execSQL(
            """
            INSERT INTO Image (fileEntryName, type, alpha, resolution)
            SELECT FileEntry.name, 'picture', '1.0', 'high'
            From FileEntry WHERE FileEntry.name LIKE '%.high'
            """
        )
    }
}