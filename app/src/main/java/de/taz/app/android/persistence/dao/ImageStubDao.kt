package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ImageStub

@Dao
interface ImageStubDao: BaseDao<ImageStub> {

    @Query("SELECT * FROM Image WHERE fileEntryName == :name")
    suspend fun getByName(name: String): ImageStub?

}