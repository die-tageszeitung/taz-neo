package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.BookmarkSynchronization

@Dao
interface BookmarkSynchronizationDao : BaseDao<BookmarkSynchronization> {

    @Query("SELECT * FROM BookmarkSynchronization WHERE mediaSyncId = :mediaSyncId")
    suspend fun get(mediaSyncId: Int): BookmarkSynchronization?
}