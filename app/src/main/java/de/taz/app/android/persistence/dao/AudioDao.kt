package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.AudioStub

@Dao
interface AudioDao : BaseDao<AudioStub> {
    @Query("SELECT * FROM Audio WHERE Audio.fileName = :audioFileName LIMIT 1")
    suspend fun get(audioFileName: String): AudioStub?

    @Query("DELETE FROM Audio WHERE Audio.fileName = :audioFileName")
    suspend fun delete(audioFileName: String)
}
