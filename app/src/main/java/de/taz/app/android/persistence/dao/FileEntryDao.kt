package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.FileEntry
import java.util.*

@Dao
abstract class FileEntryDao : BaseDao<FileEntry>() {

    @Query("SELECT * FROM FileEntry WHERE name == :name")
    abstract fun getByName(name: String): FileEntry?

    @Query("SELECT * FROM FileEntry WHERE name == :name")
    abstract fun getLiveDataByName(name: String): LiveData<FileEntry?>

    @Query("SELECT dateDownload FROM FileEntry WHERE name == :name")
    abstract fun getDownloadDate(name: String): Date?

    @Query("SELECT * FROM FileEntry WHERE name IN (:names)")
    abstract fun getByNames(names: List<String>): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE name LIKE :filterString")
    abstract fun filterByName(filterString: String): List<FileEntry>

    @Query("SELECT FileEntry.name FROM FileEntry WHERE name LIKE :filterString")
    abstract fun getNamesContaining(filterString: String): List<String>

    @Query("DELETE FROM FileEntry WHERE name in (:fileNames)")
    abstract fun deleteList(fileNames: List<String>)
}