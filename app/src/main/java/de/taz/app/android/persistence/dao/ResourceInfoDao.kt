package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ResourceInfoStub

@Dao
abstract class ResourceInfoDao: BaseDao<ResourceInfoStub>() {

    @Query("SELECT * FROM ResourceInfo ORDER BY resourceVersion DESC LIMIT 1")
    abstract fun get(): ResourceInfoStub

    @Query("DELETE FROM ResourceInfo WHERE resourceVersion NOT IN (SELECT resourceVersion from ResourceInfo ORDER BY resourceVersion DESC LIMIT 1)")
    abstract fun deleteAllButNewest()
}
