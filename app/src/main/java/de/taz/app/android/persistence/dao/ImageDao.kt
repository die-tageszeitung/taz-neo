package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.models.Image

@Dao
abstract class ImageDao: BaseDao<Image>() {

    @Query("SELECT * FROM Image WHERE name == :name AND storageType == :storageType")
    abstract fun getByNameAndStorageType(name: String, storageType: StorageType = StorageType.resource): Image?

    @Query("SELECT * FROM Image WHERE name in (:names)")
    abstract fun getByNames(names: List<String>): List<Image>

}