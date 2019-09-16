package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.join.ResourceInfoFileEntryJoin


@Dao
abstract class ResourceInfoFileEntryJoinDao: BaseDao<ResourceInfoFileEntryJoin>() {

    @Query("""SELECT FileEntry.* FROM FileEntry INNER JOIN ResourceInfoFileEntryJoin 
        ON FileEntry.name=ResourceInfoFileEntryJoin.fileEntryName 
        WHERE ResourceInfoFileEntryJoin.resourceInfoVersion=:resourceInfoVersion """)
    abstract fun getFileEntriesForResourceInfo(resourceInfoVersion: Int): List<FileEntry>
}
