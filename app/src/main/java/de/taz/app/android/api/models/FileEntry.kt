package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.FileEntryDto
import de.taz.app.android.api.interfaces.File
import de.taz.app.android.api.interfaces.StorageType
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.util.FileHelper
import kotlinx.serialization.Serializable

const val GLOBAL_FOLDER = "global"

@Entity(tableName = "FileEntry")
@Serializable
data class FileEntry(
    @PrimaryKey override val name: String,
    override val storageType: StorageType,
    override val moTime: Long,
    override val sha256: String,
    override val size: Long,
    val folder: String
) : File {
    constructor(fileEntryDto: FileEntryDto, folder: String) : this(
        fileEntryDto.name,
        fileEntryDto.storageType,
        fileEntryDto.moTime,
        fileEntryDto.sha256,
        fileEntryDto.size,
        getStorageFolder(fileEntryDto.storageType, folder)
    )

    val path
        get() = "$folder/$name"

    fun delete() {
        val fileHelper = FileHelper.getInstance()
        fileHelper.deleteFile(name)
        DownloadRepository.getInstance().delete(name)
        FileEntryRepository.getInstance().delete(this)
    }

    companion object {
        private fun getStorageFolder(storageType: StorageType, folder: String): String {
            return when (storageType) {
                StorageType.global -> GLOBAL_FOLDER
                StorageType.resource -> RESOURCE_FOLDER
                else -> folder
            }
        }
    }

}
