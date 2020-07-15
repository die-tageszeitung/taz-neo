package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.persistence.join.SectionImageJoin


@Dao
abstract class SectionImageJoinDao : BaseDao<SectionImageJoin>() {

    @Query(
        """SELECT name, storageType, moTime, sha256, size, folder, downloadedStatus, type, alpha, resolution FROM FileEntry INNER JOIN SectionImageJoin
        ON FileEntry.name = SectionImageJoin.imageFileName
        INNER Join Image ON Image.fileEntryName == SectionImageJoin.imageFileName
        WHERE SectionImageJoin.sectionFileName == :sectionFileName
        ORDER BY SectionImageJoin.`index` ASC
    """
    )
    abstract fun getImagesForSection(sectionFileName: String): List<Image>

    @Query(
        """SELECT FileEntry.name FROM FileEntry INNER JOIN SectionImageJoin
        ON FileEntry.name = SectionImageJoin.imageFileName
        WHERE SectionImageJoin.sectionFileName == :sectionFileName
        ORDER BY SectionImageJoin.`index` ASC
    """
    )
    abstract fun getImageNamesForSection(sectionFileName: String): List<String>

    fun getImagesForSectionOperation(section: SectionOperations): List<Image> =
        getImagesForSection(section.key)

}
