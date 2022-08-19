package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class MomentDto(
    val imageList: List<ImageDto>? = null,
    val creditList: List<ImageDto>? = null,
    val momentList: List<FileEntryDto>? = null
)