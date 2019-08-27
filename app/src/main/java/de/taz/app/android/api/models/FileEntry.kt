package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "FileEntry")
open class FileEntry(
    @PrimaryKey override val name: String,
    override val storageType: StorageType,
    override val moTime: String,
    override val sha256: String,
    override val size: Int
): File {
    constructor(fileEntry: FileEntry) : this(
        fileEntry.name, fileEntry.storageType, fileEntry.moTime, fileEntry.sha256, fileEntry.size
    )
}
