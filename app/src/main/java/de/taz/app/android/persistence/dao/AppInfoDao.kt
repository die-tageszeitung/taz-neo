package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.AppInfo

@Dao
interface AppInfoDao : BaseDao<AppInfo> {
    @Query("SELECT * FROM AppInfo LIMIT 1")
    suspend fun get(): AppInfo?


    @Query("SELECT COUNT(*) FROM AppInfo")
    suspend fun count(): Int

}
