package de.taz.app.android.api.models

import androidx.room.Relation

data class PodcastEpisodeAudio(
    val fileName: String?,

    @Relation(
        parentColumn = "fileName",
        entityColumn = "name"
    )
    val file: FileEntry?,

    val transcriptionName: String?,

    @Relation(
        parentColumn = "transcriptionName",
        entityColumn = "name"
    )
    val transcription: FileEntry?,

    val playtime: Int?,
    val duration: Float?,
)