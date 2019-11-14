package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Feed

@Dao
abstract class FeedDao: BaseDao<Feed>() {

    @Query("SELECT * FROM Feed WHERE Feed.name == :feedName")
    abstract fun get(feedName: String): Feed

    @Query("SELECT * FROM Feed")
    abstract fun getAll(): List<Feed>

    @Query("SELECT * FROM Feed")
    abstract fun getAllLiveData(): LiveData<List<Feed>>

}
