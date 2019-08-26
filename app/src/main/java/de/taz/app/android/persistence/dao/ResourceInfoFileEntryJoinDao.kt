package de.taz.app.android.persistence.dao

import androidx.room.*
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.join.ResourceInfoFileEntryJoin


@Dao
abstract class ResourceInfoFileEntryJoinDao: BaseDao<ResourceInfoFileEntryJoin>() {

    @Query("""SELECT * FROM FileEntry INNER JOIN ResourceInfoFileEntry 
        ON FileEntry.name=ResourceInfoFileEntry.fileEntryName 
        WHERE ResourceInfoFileEntry.resourceInfoVersion=:resourceInfoVersion""")
    abstract fun getFileEntriesForResourceInfo(resourceInfoVersion: Int): List<FileEntry>
}
