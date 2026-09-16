package de.taz.app.android.api.models

import androidx.room.Embedded
import androidx.room.Relation

data class PodcastWithEpisodes(
    @Embedded val podcast: PodcastEntity,

    @Relation(
        entity = ImageStub::class,
        parentColumn = "defaultIconFileEntryName",
        entityColumn = "fileEntryName"
    )
    val defaultIcon: ImageWithFile?,

    @Relation(
        entity = PodcastEpisodeEntity::class,
        parentColumn = "id",
        entityColumn = "podcastId"
    )
    val episodes: List<PodcastEpisodeWithDetails>
)

data class PodcastEpisodeWithDetails(
    @Embedded val episode: PodcastEpisodeEntity,

    @Relation(
        entity = ImageStub::class,
        parentColumn = "iconFileEntryName",
        entityColumn = "fileEntryName"
    )
    val icon: ImageWithFile?,

    @Relation(
        parentColumn = "audioFileName",
        entityColumn = "name"
    )
    val audioFile: FileEntry?,

    @Relation(
        parentColumn = "transcriptionFileName",
        entityColumn = "name"
    )
    val transcriptionFile: FileEntry?
)
