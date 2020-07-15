package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.persistence.join.SectionNavButtonJoin


@Dao
abstract class SectionNavButtonJoinDao : BaseDao<SectionNavButtonJoin>() {

    @Query(
        """SELECT name, storageType, moTime, sha256, size, folder, downloadedStatus, type, alpha, resolution
        FROM Image INNER JOIN SectionNavButtonJoin
        ON Image.fileEntryName = SectionNavButtonJoin.navButtonFileName
        INNER JOIN FileEntry ON FileEntry.name == Image.fileEntryName 
        WHERE SectionNavButtonJoin.sectionFileName == :sectionFileName
    """
    )
    abstract fun getNavButtonForSection(sectionFileName: String): Image

    @Query(
        """SELECT Image.fileEntryName FROM Image INNER JOIN SectionNavButtonJoin
        ON Image.fileEntryName = SectionNavButtonJoin.navButtonFileName
        INNER JOIN FileEntry ON FileEntry.name == Image.fileEntryName 
        WHERE SectionNavButtonJoin.sectionFileName == :sectionFileName
    """
    )
    abstract fun getNavButtonNameForSection(sectionFileName: String): String

    fun getNavButtonForSectionOperation(section: SectionOperations): Image =
        getNavButtonForSection(section.key)

}
