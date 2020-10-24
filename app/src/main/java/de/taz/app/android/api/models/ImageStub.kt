package de.taz.app.android.api.models

import androidx.room.Entity
import de.taz.app.android.api.dto.ImageDto
import kotlinx.serialization.Serializable

@Entity(tableName = "Image", primaryKeys = ["fileEntryName"])
@Serializable
data class ImageStub(
    val fileEntryName: String,
    val type: ImageType,
    val alpha: Float,
    val resolution: ImageResolution
) {

    constructor(imageDto: ImageDto) : this(
        fileEntryName = imageDto.name,
        type = imageDto.type,
        alpha = imageDto.alpha,
        resolution = imageDto.resolution
    )

    constructor(image: Image) : this(
        fileEntryName = image.name,
        type = image.type,
        alpha = image.alpha,
        resolution = image.resolution
    )

}

