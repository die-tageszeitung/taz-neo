package de.taz.app.android.api.models

import java.util.Date

data class PodcastEpisode(
    val id: Int,
    val pubTime: Date,
    val mediaSyncId: Int,
    val title: String?,
    val teaser: String?,
    val headLine: String?,
    val authors: String?,
    val icon: ImageWithFile?,
    val audio: PodcastEpisodeAudio,
    val alreadyPlayed: Float = 0f
)
