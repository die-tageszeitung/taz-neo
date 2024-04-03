package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index


@Entity(
    tableName = "Image",
    primaryKeys = ["fileEntryName"],
    foreignKeys = [
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["name"],
            childColumns = ["fileEntryName"]
        )
    ],
    indices = [Index("fileEntryName")]
)
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

