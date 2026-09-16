package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PodcastDto(
    val id: Int? = null,
    val name: String? = null,
    val title: String? = null,
    val displayName: String? = null,
    val version: Int? = null,
    val useDefaultIcon: Boolean? = null,
    val defaultIcon: PodcastImageDto? = null,
    val episodeCnt: Int? = null,
    val episodeList: List<PodcastEpisodeDto>? = null,
)
