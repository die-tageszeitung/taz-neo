package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import de.taz.app.android.api.models.PodcastEntity
import de.taz.app.android.api.models.PodcastEpisodeEntity
import de.taz.app.android.api.models.PodcastWithEpisodes
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao : BaseDao<PodcastEntity> {

    @Transaction
    @Query("SELECT * FROM Podcasts ORDER BY id ASC")
    fun observePodcastsWithEpisodes(): Flow<List<PodcastWithEpisodes>>

    @Transaction
    @Query("SELECT * FROM Podcasts WHERE id = :podcastId")
    suspend fun getPodcastWithEpisodes(podcastId: Int): PodcastWithEpisodes?

    @Query("DELETE FROM Podcasts")
    suspend fun deleteAllPodcasts()

    @Transaction
    suspend fun insertPodcastWithEpisodes(
        podcast: PodcastEntity,
        episodes: List<PodcastEpisodeEntity>
    ) {
        insertOrReplace(podcast)
        insertEpisodes(episodes)
    }

    @Query("DELETE FROM PodcastEpisodes WHERE podcastId = :podcastId")
    suspend fun deleteEpisodesForPodcast(podcastId: Int)

    @Query("UPDATE PodcastEpisodes SET alreadyPlayed = :alreadyPlayed WHERE id = :episodeId")
    suspend fun updateAlreadyPlayed(episodeId: Int, alreadyPlayed: Float)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<PodcastEpisodeEntity>)
}
