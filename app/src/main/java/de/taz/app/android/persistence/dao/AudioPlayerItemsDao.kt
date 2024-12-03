package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.AudioPlayerItemStub

@Dao
interface AudioPlayerItemsDao : BaseDao<AudioPlayerItemStub> {
    @Query("SELECT * FROM Playlist")
    suspend fun getAll(): List<AudioPlayerItemStub>

    @Query("DELETE FROM Playlist WHERE audioPlayerItemId NOT IN (:listOfIds)")
    suspend fun deleteAllBut(listOfIds: List<String>)
}