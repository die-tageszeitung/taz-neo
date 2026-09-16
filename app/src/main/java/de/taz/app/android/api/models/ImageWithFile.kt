package de.taz.app.android.api.models

import androidx.room.Embedded
import androidx.room.Relation

data class ImageWithFile(
    @Embedded val imageStub: ImageStub,
    @Relation(
        parentColumn = "fileEntryName",
        entityColumn = "name"
    )
    val fileEntry: FileEntry?
) {
    // Helper constructor to wrap an Image model
    constructor(image: Image) : this(ImageStub(image), FileEntry(image))
}
