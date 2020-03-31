package de.taz.app.android.api.dto

import de.taz.app.android.api.models.ImageResolution
import de.taz.app.android.api.models.ImageType

data class ImageDto(
    val name: String,
    val storageType: StorageType,
    val moTime: Long,
    val sha256: String,
    val size: Long,
    val type: ImageType,
    val alpha: Float,
    val resolution: ImageResolution
)
