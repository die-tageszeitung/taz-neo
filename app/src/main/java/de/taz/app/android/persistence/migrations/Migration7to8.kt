package de.taz.app.android.persistence.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration7to8 : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {

        db.execSQL("REPLACE INTO FileEntry SELECT name, storageType, moTime, sha256, size, folder FROM Image")

        db.execSQL("CREATE TABLE ImageStub (`fileEntryName` TEXT NOT NULL, `type` TEXT NOT NULL, `alpha` REAL NOT NULL, `resolution` TEXT NOT NULL, PRIMARY KEY(`fileEntryName`))")
        db.execSQL("INSERT INTO ImageStub (fileEntryName, type, alpha, resolution) SELECT name, type, alpha, resolution FROM Image")

        db.execSQL("DROP TABLE Image")

        db.execSQL("ALTER TABLE ImageStub RENAME TO Image")

        db.execSQL("CREATE TABLE SNBJ2 (`sectionFileName` TEXT NOT NULL, `navButtonFileName` TEXT NOT NULL, PRIMARY KEY(`sectionFileName`, `navButtonFileName`), FOREIGN KEY(`sectionFileName`) REFERENCES `Section`(`sectionFileName`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`navButtonFileName`) REFERENCES `Image`(`fileEntryName`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
        db.execSQL("INSERT INTO SNBJ2 (sectionFileName, navButtonFileName) SELECT sectionFileName, navButtonFileName FROM SectionNavButtonJoin")
        db.execSQL("DROP TABLE SectionNavButtonJoin")
        db.execSQL("ALTER TABLE SNBJ2 RENAME TO SectionNavButtonJoin")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_SectionNavButtonJoin_sectionFileName` ON SectionNavButtonJoin(`sectionFileName`)")

        db.execSQL(
            """
            INSERT INTO Image (fileEntryName, type, alpha, resolution)
            SELECT FileEntry.name, 'picture', '1.0', 'small'
            From FileEntry WHERE FileEntry.name LIKE '%.small.%'
            """
        )

        db.execSQL(
            """
            INSERT INTO Image (fileEntryName, type, alpha, resolution)
            SELECT FileEntry.name, 'picture', '1.0', 'normal'
            From FileEntry WHERE FileEntry.name LIKE '%.norm.%'
            """
        )

        db.execSQL(
            """
            INSERT INTO Image (fileEntryName, type, alpha, resolution)
            SELECT FileEntry.name, 'picture', '1.0', 'normal'
            From FileEntry WHERE FileEntry.name LIKE '%.quadrat.%'
            """
        )
        db.execSQL(
            """
            INSERT INTO Image (fileEntryName, type, alpha, resolution)
            SELECT FileEntry.name, 'picture', '1.0', 'high'
            From FileEntry WHERE FileEntry.name LIKE '%.high.%'
            """
        )
    }
}