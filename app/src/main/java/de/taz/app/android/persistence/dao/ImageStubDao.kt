package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ImageStub

@Dao
abstract class ImageStubDao: BaseDao<ImageStub>() {

    @Query("SELECT * FROM Image WHERE fileEntryName == :name")
    abstract fun getByName(name: String): ImageStub?

    @Query("SELECT * FROM Image WHERE fileEntryName IN (:names)")
    abstract fun getByNames(names: List<String>): List<ImageStub>

}