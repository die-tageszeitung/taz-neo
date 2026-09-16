package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PodcastEpisodeDto(
    val id: Int? = null,
    val pubTime: String? = null,
    val mediaSyncId: Int? = null,
    val title: String? = null,
    val teaser: String? = null,
    val headLine: String? = null,
    val authors: String? = null,
    val icon: PodcastImageDto? = null,
    val audio: PodcastEpisodeAudioDto? = null,
)
