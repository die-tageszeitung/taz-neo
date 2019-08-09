package de.taz.app.android.api.models

data class Image (
    val  resolution: Resolution,
    override val name: String,
    override val storageType: StorageType,
    val type: ImageType,
    override val moTime: String,
    override val sha256: String,
    override val size: Int
): File

enum class Resolution {
    small,
    normal,
    high
}

enum class ImageType {
    picture,
    advertisement,
    facsimile
}