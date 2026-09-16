package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PodcastEpisodeAudioDto(
    val file: PodcastFileDto? = null,
    val transcription: PodcastFileDto? = null,
    val playtime: Int? = null,
    val duration: Float? = null,
)
