package de.taz.app.android.api.dto

data class Image(
    val name: String,
    val storageType: StorageType,
    val moTime: Long,
    val sha256: String,
    val size: Long,
    val type: ImageType,
    val alpha: Float,
    val resolution: ImageResolution,

)

enum class ImageType {
    picture,
    advertisement,
    facsimile
}

enum class ImageResolution {
    small,
    normal,
    high
}