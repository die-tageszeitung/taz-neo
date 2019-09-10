package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "FileEntry")
@Serializable
data class FileEntry(
    @PrimaryKey override val name: String,
    override val storageType: StorageType,
    override val moTime: Long,
    override val sha256: String,
    override val size: Int
): File {
    constructor(fileEntry: FileEntry) : this(
        fileEntry.name, fileEntry.storageType, fileEntry.moTime, fileEntry.sha256, fileEntry.size
    )

    override fun equals(other: Any?): Boolean {
        // TODO replace one there is no more bad data from server…
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as FileEntry

        return name == other.name
    }
}
