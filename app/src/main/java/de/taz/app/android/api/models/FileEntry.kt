package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.File
import de.taz.app.android.api.interfaces.StorageType
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.util.FileHelper
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

    fun delete() {
        FileHelper.getInstance().deleteFile(name)
        DownloadRepository.getInstance().delete(name)
        FileEntryRepository.getInstance().delete(this)
    }
}
