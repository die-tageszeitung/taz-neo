package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.PodcastDto
import de.taz.app.android.api.dto.PodcastEpisodeAudioDto
import de.taz.app.android.api.dto.PodcastEpisodeDto
import de.taz.app.android.api.dto.PodcastFileDto
import de.taz.app.android.api.dto.PodcastImageDto
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.ImageResolution
import de.taz.app.android.api.models.ImageType
import de.taz.app.android.api.models.ImageWithFile
import de.taz.app.android.api.models.Podcast
import de.taz.app.android.api.models.PodcastEntity
import de.taz.app.android.api.models.PodcastEpisode
import de.taz.app.android.api.models.PodcastEpisodeAudio
import de.taz.app.android.api.models.PodcastEpisodeEntity
import de.taz.app.android.api.models.PodcastEpisodeWithDetails
import de.taz.app.android.api.models.PodcastWithEpisodes
import de.taz.app.android.api.models.StorageType
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.StorageService

object PodcastMapper {
    fun from(podcastDto: PodcastDto): Podcast {
        return Podcast(
            id = requireNotNull(podcastDto.id),
            name = requireNotNull(podcastDto.name),
            title = requireNotNull(podcastDto.title),
            displayName = requireNotNull(podcastDto.displayName),
            version = requireNotNull(podcastDto.version),
            useDefaultIcon = podcastDto.useDefaultIcon ?: true,
            defaultIcon = requireNotNull(
                mapImage(podcastDto.defaultIcon)
            ),
            episodeCnt = requireNotNull(podcastDto.episodeCnt),
            episodeList = podcastDto.episodeList?.map { from(it) }?.sortedByDescending { it.pubTime } ?: emptyList()
        )
    }

    private fun from(episodeDto: PodcastEpisodeDto): PodcastEpisode {
        return PodcastEpisode(
            id = requireNotNull(episodeDto.id),
            pubTime = simpleDateFormat.parse(
                requireNotNull(episodeDto.pubTime)
            )!!,
            mediaSyncId = requireNotNull(episodeDto.mediaSyncId),
            title = episodeDto.title,
            teaser = episodeDto.teaser,
            headLine = episodeDto.headLine,
            authors = episodeDto.authors,
            icon = mapImage(episodeDto.icon),
            audio = requireNotNull(episodeDto.audio?.let { from(it) }),
            alreadyPlayed = 0f
        )
    }

    private fun from(audioDto: PodcastEpisodeAudioDto): PodcastEpisodeAudio {
        return PodcastEpisodeAudio(
            fileName = audioDto.file?.name,
            file = mapFile(audioDto.file),
            transcriptionName = audioDto.transcription?.name,
            transcription = mapFile(audioDto.transcription),
            playtime = audioDto.playtime,
            duration = audioDto.duration
        )
    }

    private fun mapImage(imageDto: PodcastImageDto?): ImageWithFile? {
        if (imageDto == null) return null
        val name = imageDto.name
        val storageType = StorageType.global
        val path = StorageService.determineFilePath(storageType, name, null)

        val image = Image(
            name = name,
            storageType = storageType,
            moTime = 0,
            sha256 = "",
            size = 0,
            path = path,
            type = ImageType.picture,
            alpha = 1.0f,
            resolution = ImageResolution.normal,
            dateDownload = null,
            storageLocation = StorageLocation.NOT_STORED
        )
        return ImageWithFile(image)
    }

    private fun mapFile(fileDto: PodcastFileDto?): FileEntry? {
        if (fileDto == null) return null
        val storageType = StorageType.global
        val path = StorageService.determineFilePath(storageType, fileDto.name, null)
        return FileEntry(
            name = fileDto.name,
            storageType = storageType,
            moTime = 0,
            sha256 = "",
            size = 0,
            path = path,
            dateDownload = null,
            storageLocation = StorageLocation.NOT_STORED
        )
    }

    fun toEntity(podcast: Podcast): PodcastEntity {
        return PodcastEntity(
            id = podcast.id,
            name = podcast.name,
            title = podcast.title,
            displayName = podcast.displayName,
            version = podcast.version,
            useDefaultIcon = podcast.useDefaultIcon,
            defaultIconFileEntryName = podcast.defaultIcon.imageStub.fileEntryName,
            episodeCnt = podcast.episodeCnt
        )
    }

    fun toEpisodeEntity(episode: PodcastEpisode, podcastId: Int): PodcastEpisodeEntity {
        return PodcastEpisodeEntity(
            id = episode.id,
            podcastId = podcastId,
            pubTime = episode.pubTime,
            mediaSyncId = episode.mediaSyncId,
            title = episode.title,
            teaser = episode.teaser,
            headLine = episode.headLine,
            authors = episode.authors,
            iconFileEntryName = episode.icon?.imageStub?.fileEntryName,
            audioFileName = episode.audio.fileName,
            transcriptionFileName = episode.audio.transcriptionName,
            playtime = episode.audio.playtime,
            duration = episode.audio.duration,
            alreadyPlayed = episode.alreadyPlayed
        )
    }

    fun fromEntity(podcastWithEpisodes: PodcastWithEpisodes): Podcast {
        val podcastEntity = podcastWithEpisodes.podcast
        return Podcast(
            id = podcastEntity.id,
            name = podcastEntity.name,
            title = podcastEntity.title,
            displayName = podcastEntity.displayName,
            version = podcastEntity.version,
            useDefaultIcon = podcastEntity.useDefaultIcon,
            defaultIcon = podcastWithEpisodes.defaultIcon ?: mapImage(PodcastImageDto(""))!!,
            episodeCnt = podcastEntity.episodeCnt,
            episodeList = podcastWithEpisodes.episodes
                .map { fromEntity(it) }
                .sortedByDescending { it.pubTime }
        )
    }

    fun fromEntity(episodeWithDetails: PodcastEpisodeWithDetails): PodcastEpisode {
        val entity = episodeWithDetails.episode
        return PodcastEpisode(
            id = entity.id,
            pubTime = entity.pubTime,
            mediaSyncId = entity.mediaSyncId,
            title = entity.title,
            teaser = entity.teaser,
            headLine = entity.headLine,
            authors = entity.authors,
            icon = episodeWithDetails.icon,
            audio = PodcastEpisodeAudio(
                fileName = entity.audioFileName,
                file = episodeWithDetails.audioFile,
                transcriptionName = entity.transcriptionFileName,
                transcription = episodeWithDetails.transcriptionFile,
                playtime = entity.playtime,
                duration = entity.duration
            ),
            alreadyPlayed = entity.alreadyPlayed
        )
    }
}
