package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.models.ImageResolution
import de.taz.app.android.api.models.ImageType

@JsonClass(generateAdapter = true)
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
