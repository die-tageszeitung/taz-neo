package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.mappers.PodcastMapper
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.ImageWithFile
import de.taz.app.android.api.models.Podcast
import de.taz.app.android.content.cache.ContentDownload
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.dao.PodcastDao
import de.taz.app.android.singletons.StoragePathService
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class PodcastRepository private constructor(private val context: Context) : RepositoryBase(context) {

    private val apiService = ApiService.getInstance(context)
    private val podcastDao: PodcastDao = appDatabase.podcastDao()
    private val fileEntryRepository = FileEntryRepository.getInstance(context)
    private val imageRepository = ImageRepository.getInstance(context)
    private val storagePathService = StoragePathService.getInstance(context)
    private val storageService = StorageService.getInstance(context)

    companion object : SingletonHolder<PodcastRepository, Context>(::PodcastRepository)

    fun observePodcasts(): Flow<List<Podcast>> {
        return podcastDao.observePodcastsWithEpisodes().map { list ->
            list.map { PodcastMapper.fromEntity(it) }
        }
    }

    suspend fun refreshPodcasts(limit: Int) = withContext(Dispatchers.IO) {
        val podcasts = apiService.getPodcastList(limit)
        if (podcasts != null) {
            savePodcasts(podcasts)
            downloadIcons(podcasts)
        }
    }

    private suspend fun savePodcasts(podcasts: List<Podcast>) {
        podcasts.forEach { podcast ->
            savePodcast(podcast)
        }
    }

    private suspend fun savePodcast(podcast: Podcast) {
        // Get existing podcasts
        val existingWithEpisodes = podcastDao.getPodcastWithEpisodes(podcast.id)
        val progressMap = existingWithEpisodes?.episodes?.associate {
            it.episode.id to it.episode.alreadyPlayed
        } ?: emptyMap()

        // Map the given ones with the alreadyPlayed time
        val podcastEntity = PodcastMapper.toEntity(podcast)
        val episodeEntities = podcast.episodeList.map { episode ->
            PodcastMapper.toEpisodeEntity(episode, podcast.id).copy(
                alreadyPlayed = progressMap[episode.id] ?: 0f,
                iconFileEntryName = if (episode.icon == null) {
                    podcast.defaultIcon.imageStub.fileEntryName
                } else {
                    episode.icon.imageStub.fileEntryName
                }
            )
        }

        // Skip write and return if data hasn't changed
        if (existingWithEpisodes != null &&
            existingWithEpisodes.podcast == podcastEntity &&
            existingWithEpisodes.episodes.map { it.episode } == episodeEntities
        ) {
            return
        }

        // Save icon
        saveImageWithFile(podcast.defaultIcon)

        // Save episodes' icons and audio files
        podcast.episodeList.forEach { episode ->
            episode.icon?.let { saveImageWithFile(it) }
            episode.audio.file?.let { fileEntryRepository.save(it) }
            episode.audio.transcription?.let { fileEntryRepository.save(it) }
        }

        podcastDao.insertPodcastWithEpisodes(podcastEntity, episodeEntities)
    }

    suspend fun updateAlreadyPlayed(episodeId: Int, alreadyPlayed: Float) = withContext(Dispatchers.IO) {
        podcastDao.updateAlreadyPlayed(episodeId, alreadyPlayed)
    }

    private suspend fun saveImageWithFile(imageWithFile: ImageWithFile) {
        imageWithFile.fileEntry?.let { fileEntryRepository.save(it) }
        imageRepository.saveOrReplace(imageWithFile.imageStub)
    }

    /**
     * Download podcast and its episode icons if not already downloaded.
     *
     * @param podcasts List of podcasts to download icons for.
     */
    private suspend fun downloadIcons(podcasts: List<Podcast>) {
        val allFileEntries = mutableListOf<FileEntry>()
        podcasts.forEach { podcast ->
            podcast.defaultIcon.fileEntry?.let { allFileEntries.add(it) }
            podcast.episodeList.forEach { episode ->
                episode.icon?.fileEntry?.let { allFileEntries.add(it) }
            }
        }

        allFileEntries.distinctBy { it.name }.forEach { fileEntry ->
            val existingEntry = fileEntryRepository.get(fileEntry.name)
            if (existingEntry?.dateDownload != null) {
                val path = storageService.getAbsolutePath(existingEntry)
                if (path != null && File(path).exists()) {
                    return@forEach
                }
            }

            try {
                val baseUrl = storagePathService.determineBaseUrl(fileEntry)
                val download = ContentDownload.prepare(
                    context,
                    fileEntry,
                    baseUrl,
                    DownloadPriority.High
                )
                download.execute()
                // Update file entry in DB after download
                fileEntryRepository.get(fileEntry.name)?.let { updated ->
                    fileEntryRepository.save(updated)
                }
            } catch (_: Exception) {
                // Ignore for now
            }
        }
    }
}
