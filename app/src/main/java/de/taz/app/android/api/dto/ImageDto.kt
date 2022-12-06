package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ImageDto(
    val name: String,
    val storageType: StorageTypeDto,
    val moTime: Long,
    val sha256: String,
    val size: Long,
    val type: ImageTypeDto,
    val alpha: Float,
    val resolution: ImageResolutionDto
)
