package de.taz.app.android.api.models

import androidx.room.Entity

@Entity(tableName = "Image", primaryKeys = ["fileEntryName"])
data class ImageStub(
    val fileEntryName: String,
    val type: ImageType,
    val alpha: Float,
    val resolution: ImageResolution
) {
    constructor(image: Image) : this(
        fileEntryName = image.name,
        type = image.type,
        alpha = image.alpha,
        resolution = image.resolution
    )
}

