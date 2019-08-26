package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.persistence.BaseDao
import de.taz.app.android.persistence.entities.ResourceInfoEntity

@Dao
abstract class ResourceInfoDao: BaseDao<ResourceInfoEntity>() {

    @Query("SELECT * FROM ResourceInfo ORDER BY resourceVersion DESC LIMIT 1")
    abstract fun get(): ResourceInfoEntity

    @Query("DELETE FROM ResourceInfo WHERE resourceVersion NOT IN (SELECT resourceVersion from ResourceInfo ORDER BY resourceVersion DESC LIMIT 1)")
    abstract fun deleteAllButNewest()
}
