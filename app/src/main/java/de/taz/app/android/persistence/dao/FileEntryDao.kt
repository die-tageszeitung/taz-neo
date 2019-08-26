package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.FileEntry

@Dao
abstract class FileEntryDao: BaseDao<FileEntry>() {

    @Query("SELECT * FROM FileEntry WHERE name == :name")
    abstract fun getByName(name: String): FileEntry

    @Query("SELECT * FROM FileEntry WHERE name in(:names)")
    abstract fun getByNames(names: List<String>): List<FileEntry>

}