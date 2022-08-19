package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Image

@Dao
interface ImageDao {

    @Query(
        """
        SELECT name, storageType, moTime, sha256, size, folder, type, alpha, resolution, dateDownload, path, storageLocation FROM Image INNER JOIN FileEntry ON FileEntry.name == Image.fileEntryName
         WHERE fileEntryName == :name
        """
    )
    suspend fun getByName(name: String): Image?

    @Query(
        """
        SELECT name, storageType, moTime, sha256, size, folder, type, alpha, resolution, dateDownload, path, storageLocation FROM Image INNER JOIN FileEntry ON FileEntry.name == Image.fileEntryName
         WHERE fileEntryName == :name
        """
    )
    fun getLiveDataByName(name: String): LiveData<Image?>

    @Query(
        """
        SELECT name, storageType, moTime, sha256, size, folder, type, alpha, resolution, dateDownload, path, storageLocation FROM Image INNER JOIN FileEntry ON FileEntry.name == Image.fileEntryName
         WHERE fileEntryName IN (:names)
        """
    )
    suspend fun getByNames(names: List<String>): List<Image>

    @Query(
        """
        SELECT Image.fileEntryName FROM Image
         WHERE fileEntryName IN (:names)
        """
    )
    suspend fun getNames(names: List<String>): List<String>

}