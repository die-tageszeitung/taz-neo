package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.FileEntry

@Dao
abstract class FileEntryDao : BaseDao<FileEntry>() {

    @Query("SELECT * FROM FileEntry WHERE name == :name")
    abstract fun getByName(name: String): FileEntry?

    @Query("SELECT * FROM FileEntry WHERE name == :name")
    abstract fun getLiveDataByName(name: String): LiveData<FileEntry?>

    @Query("SELECT * FROM FileEntry WHERE name IN (:names)")
    abstract fun getByNames(names: List<String>): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE name LIKE :filterString")
    abstract fun filterByName(filterString: String): List<FileEntry>

    @Query("SELECT FileEntry.name FROM FileEntry WHERE name LIKE :filterString")
    abstract fun getNamesContaining(filterString: String): List<String>

    @Query("SELECT EXISTS(SELECT * FROM FileEntry WHERE name == :fileName AND downloadedStatus == 'done')")
    abstract fun isDownloadedLiveData(fileName: String): LiveData<Boolean>
}