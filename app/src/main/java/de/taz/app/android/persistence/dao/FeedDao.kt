package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Feed
import java.util.*

@Dao
interface FeedDao: BaseDao<Feed> {

    @Query("SELECT * FROM Feed WHERE Feed.name == :feedName")
    suspend fun get(feedName: String): Feed?

    @Query("SELECT * FROM Feed")
    suspend fun getAll(): List<Feed>

    @Query("SELECT publicationDates FROM Feed WHERE name == :feedName")
    suspend fun getPublicationDates(feedName: String): List<Date>

}
