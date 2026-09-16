package de.taz.app.android.api.models

data class Podcast(
    val id: Int,
    val name: String,
    val title: String,
    val displayName: String,
    val version: Int,
    val useDefaultIcon: Boolean,
    val defaultIcon: ImageWithFile,
    val episodeCnt: Int,
    val episodeList: List<PodcastEpisode>,
)
