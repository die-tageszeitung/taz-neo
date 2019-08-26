package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ResourceInfoWithoutFiles

@Dao
abstract class ResourceInfoDao: BaseDao<ResourceInfoWithoutFiles>() {

    @Query("SELECT * FROM ResourceInfo ORDER BY resourceVersion DESC LIMIT 1")
    abstract fun get(): ResourceInfoWithoutFiles

    @Query("DELETE FROM ResourceInfo WHERE resourceVersion NOT IN (SELECT resourceVersion from ResourceInfo ORDER BY resourceVersion DESC LIMIT 1)")
    abstract fun deleteAllButNewest()
}
