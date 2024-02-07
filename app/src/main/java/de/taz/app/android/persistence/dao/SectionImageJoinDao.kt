package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Image
import de.taz.app.android.persistence.join.SectionImageJoin


@Dao
interface SectionImageJoinDao : BaseDao<SectionImageJoin> {

    @Query(
        """SELECT name, storageType, moTime, sha256, size, folder, type, alpha, resolution, dateDownload, path, storageLocation FROM FileEntry INNER JOIN SectionImageJoin
        ON FileEntry.name = SectionImageJoin.imageFileName
        INNER Join Image ON Image.fileEntryName == SectionImageJoin.imageFileName
        WHERE SectionImageJoin.sectionFileName == :sectionFileName
        ORDER BY SectionImageJoin.`index` ASC
    """
    )
    suspend fun getImagesForSection(sectionFileName: String): List<Image>

    @Query("DELETE FROM SectionImageJoin WHERE sectionFileName = :sectionFileName")
    suspend fun deleteRelationToSection(sectionFileName: String)

}
