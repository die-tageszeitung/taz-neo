package de.taz.app.android.persistence.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.StorageType

@Entity(tableName = "FileEntry")
data class FileEntryEntity(
    @PrimaryKey val name: String,
    val storageType: StorageType,
    val moTime: String,
    val sha256: String,
    val size: Int
) : GenericEntity<FileEntry>() {

    constructor(fileEntry: FileEntry) : this(
        fileEntry.name, fileEntry.storageType, fileEntry.moTime, fileEntry.sha256, fileEntry.size
    )

    override fun toObject(): FileEntry {
        return FileEntry(name, storageType, moTime, sha256, size)
    }
}
