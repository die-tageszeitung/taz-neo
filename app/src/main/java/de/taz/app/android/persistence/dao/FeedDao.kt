package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Feed
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface FeedDao: BaseDao<Feed> {

    @Query("SELECT * FROM Feed WHERE Feed.name == :feedName")
    fun get(feedName: String): Flow<Feed?>
}
