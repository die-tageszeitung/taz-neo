package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.DownloadStub

@Dao
abstract class DownloadDao : BaseDao<DownloadStub>() {
    @Query("SELECT * FROM Download WHERE Download.fileName == :fileName LIMIT 1")
    abstract fun get(fileName: String): DownloadStub?

    @Query("SELECT * FROM Download WHERE Download.fileName in(:fileNames)")
    abstract fun get(fileNames: List<String>): List<DownloadStub?>

    @Query("SELECT * FROM Download WHERE Download.fileName == :fileName LIMIT 1")
    abstract fun getLiveData(fileName: String): LiveData<DownloadStub?>

}
