package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.DownloadWithoutFile

@Dao
abstract class DownloadDao : BaseDao<DownloadWithoutFile>() {
    @Query("SELECT * FROM Download WHERE Download.fileName == :fileName LIMIT 1")
    abstract fun get(fileName: String): DownloadWithoutFile?

    @Query("SELECT * FROM Download WHERE Download.fileName in(:fileNames)")
    abstract fun get(fileNames: List<String>): List<DownloadWithoutFile?>

    @Query("SELECT * FROM Download WHERE Download.fileName == :fileName LIMIT 1")
    abstract fun getLiveData(fileName: String): LiveData<DownloadWithoutFile?>

}
