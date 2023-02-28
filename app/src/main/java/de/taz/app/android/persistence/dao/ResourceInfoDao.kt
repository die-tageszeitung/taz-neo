package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ResourceInfoStub
import java.util.*

@Dao
interface ResourceInfoDao: BaseDao<ResourceInfoStub> {

    @Query("SELECT * FROM ResourceInfo ORDER BY resourceVersion DESC LIMIT 1")
    suspend fun getNewest(): ResourceInfoStub?

    @Query("SELECT * FROM ResourceInfo WHERE dateDownload IS NOT NULL ORDER BY resourceVersion DESC LIMIT 1")
    suspend fun getNewestDownloaded(): ResourceInfoStub?

    @Query("SELECT dateDownload FROM ResourceInfo WHERE resourceVersion == :resourceVersion")
    suspend fun getDownloadStatus(resourceVersion: Int): Date?
}
