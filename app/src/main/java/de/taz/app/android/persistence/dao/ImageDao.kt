package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Image

@Dao
abstract class ImageDao() {

    @Query(
        """
        SELECT * FROM Image INNER JOIN FileEntry on FileEntry.name == Image.fileEntryName
         WHERE fileEntryName == :name
        """
    )
    abstract fun getByName(name: String): Image?

    @Query(
        """
        SELECT * FROM Image INNER JOIN FileEntry on FileEntry.name == Image.fileEntryName
         WHERE fileEntryName in (:names)
        """
    )
    abstract fun getByNames(names: List<String>): List<Image>

}