package de.taz.app.android.persistence.dao

import androidx.room.*
import de.taz.app.android.persistence.BaseDao
import de.taz.app.android.persistence.entities.FileEntryEntity
import de.taz.app.android.persistence.entities.ResourceInfoFileEntryJoin


@Dao
abstract class ResourceInfoFileEntryJoinDao: BaseDao<ResourceInfoFileEntryJoin>() {

    @Query("""SELECT * FROM FileEntry INNER JOIN ResourceInfoFileEntry 
        ON FileEntry.name=ResourceInfoFileEntry.fileEntryName 
        WHERE ResourceInfoFileEntry.resourceInfoVersion=:resourceInfoVersion""")
    abstract fun getFileEntriesForResourceInfo(resourceInfoVersion: Int): List<FileEntryEntity>
}
