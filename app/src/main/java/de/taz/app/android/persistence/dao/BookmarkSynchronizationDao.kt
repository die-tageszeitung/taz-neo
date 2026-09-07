package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import de.taz.app.android.api.models.BookmarkSynchronization
import de.taz.app.android.api.models.BookmarkSynchronizationLocallyChangedTime

@Dao
interface BookmarkSynchronizationDao : BaseDao<BookmarkSynchronization> {

    @Query("SELECT * FROM BookmarkSynchronization WHERE mediaSyncId = :mediaSyncId")
    suspend fun get(mediaSyncId: Int): BookmarkSynchronization?

    @Query("DELETE FROM BookmarkSynchronization")
    suspend fun deleteAll()

    @Update
    suspend fun updateAll(bookmarkSynchronizations: List<BookmarkSynchronization>)

    @Update(entity = BookmarkSynchronization::class)
    suspend fun markAsChangedLocally(bookmarkSynchronizations: List<BookmarkSynchronizationLocallyChangedTime>)
}