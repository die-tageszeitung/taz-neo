package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Image

@Dao
abstract class ImageDao() {

    @Query(
        """
        SELECT name, storageType, moTime, sha256, size, folder, downloadedStatus, type, alpha, resolution FROM Image INNER JOIN FileEntry ON FileEntry.name == Image.fileEntryName
         WHERE fileEntryName == :name
        """
    )
    abstract fun getByName(name: String): Image?

    @Query(
        """
        SELECT name, storageType, moTime, sha256, size, folder, downloadedStatus, type, alpha, resolution FROM Image INNER JOIN FileEntry ON FileEntry.name == Image.fileEntryName
         WHERE fileEntryName == :name
        """
    )
    abstract fun getLiveDataByName(name: String): LiveData<Image?>

    @Query(
        """
        SELECT name, storageType, moTime, sha256, size, folder, downloadedStatus, type, alpha, resolution FROM Image INNER JOIN FileEntry ON FileEntry.name == Image.fileEntryName
         WHERE fileEntryName IN (:names)
        """
    )
    abstract fun getByNames(names: List<String>): List<Image>

    @Query(
        """
        SELECT Image.fileEntryName FROM Image
         WHERE fileEntryName IN (:names)
        """
    )
    abstract fun getNames(names: List<String>): List<String>

}