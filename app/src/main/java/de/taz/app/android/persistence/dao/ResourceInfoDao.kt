package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ResourceInfoStub
import java.util.*

@Dao
abstract class ResourceInfoDao: BaseDao<ResourceInfoStub>() {

    @Query("SELECT * FROM ResourceInfo ORDER BY resourceVersion DESC LIMIT 1")
    abstract fun getNewest(): ResourceInfoStub?

    @Query("SELECT * FROM ResourceInfo WHERE dateDownload != null ORDER BY resourceVersion DESC LIMIT 1")
    abstract fun getNewestDownloadedLiveData(): LiveData<ResourceInfoStub?>

    @Query("SELECT * FROM ResourceInfo WHERE dateDownload != null ORDER BY resourceVersion DESC LIMIT 1")
    abstract fun getNewestDownloaded(): ResourceInfoStub?

    @Query("SELECT * FROM ResourceInfo ORDER BY resourceVersion DESC LIMIT 1")
    abstract fun getLiveData(): LiveData<ResourceInfoStub?>

    @Query("SELECT * FROM ResourceInfo ORDER BY resourceVersion")
    abstract fun getAll(): List<ResourceInfoStub>

    @Query("SELECT EXISTS (SELECT * FROM ResourceInfo WHERE resourceVersion == :resourceVersion AND dateDownload != null)")
    abstract fun isDownloadedLiveData(resourceVersion: Int): LiveData<Boolean>

    @Query("SELECT dateDownload FROM ResourceInfo WHERE resourceVersion == :resourceVersion")
    abstract fun getDownloadStatus(resourceVersion: Int): Date?
}
