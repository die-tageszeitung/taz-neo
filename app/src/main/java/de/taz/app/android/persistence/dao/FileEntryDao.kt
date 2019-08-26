package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.TypeConverter
import de.taz.app.android.api.models.StorageType
import de.taz.app.android.persistence.BaseDao
import de.taz.app.android.api.models.FileEntry

@Dao
abstract class FileEntryDao: BaseDao<FileEntry>() {

    @Query("SELECT * FROM FileEntry WHERE name == :name")
    abstract fun getByName(name: String): FileEntry

    @Query("SELECT * FROM FileEntry WHERE name in(:names)")
    abstract fun getByNames(names: List<String>): List<FileEntry>

}

class StorageTypeConverter {
    @TypeConverter
    fun toString(storageType: StorageType): String {
        return storageType.name
    }

    @TypeConverter
    fun toStorageType(value: String): StorageType {
        return StorageType.valueOf(value)
    }

}