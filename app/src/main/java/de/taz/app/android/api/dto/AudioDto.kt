package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class AudioDto(
    val file: FileEntryDto?,
    val playtime: Int?,
    val duration: Float?,
    val speaker: AudioSpeakerDto?,
    val breaks: FloatArray?,
)
