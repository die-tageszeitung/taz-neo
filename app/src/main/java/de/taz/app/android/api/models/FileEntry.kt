package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.FileEntryDto
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.singletons.FileHelper
import kotlinx.serialization.Serializable

const val GLOBAL_FOLDER = "global"

@Entity(tableName = "FileEntry")
@Serializable
data class FileEntry(
    @PrimaryKey val name: String,
    val storageType: StorageType,
    val moTime: Long,
    val sha256: String,
    val size: Long,
    val folder: String
) {

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

    fun deleteFile() {
        val fileHelper = FileHelper.getInstance()
        fileHelper.deleteFile(name)
        DownloadRepository.getInstance().delete(name)
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
