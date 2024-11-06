package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Image

@Dao
interface ImageDao {

    @Query(
        """
        SELECT name, storageType, moTime, sha256, size, type, alpha, resolution, dateDownload, path, storageLocation FROM Image INNER JOIN FileEntry ON FileEntry.name == Image.fileEntryName
         WHERE fileEntryName == :name
        """
    )
    suspend fun getByName(name: String): Image?

    @Query(
        """
        SELECT name, storageType, moTime, sha256, size, type, alpha, resolution, dateDownload, path, storageLocation FROM Image INNER JOIN FileEntry ON FileEntry.name == Image.fileEntryName
         WHERE fileEntryName IN (:names)
        """
    )
    suspend fun getByNames(names: List<String>): List<Image>

}