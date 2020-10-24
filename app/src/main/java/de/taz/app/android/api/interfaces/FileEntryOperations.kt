package de.taz.app.android.api.interfaces

import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.FileHelper
import java.util.*

interface FileEntryOperations {
    val name: String
    val storageType: StorageType
    val moTime: Long
    val sha256: String
    val size: Long
    val folder: String
    val dateDownload: Date?

    val path
        get() = "$folder/$name"

    fun deleteFile() {
        val fileHelper = FileHelper.getInstance()
        fileHelper.deleteFile(name)
        val fileEntry = if (this is Image) {
            FileEntry(this)
        } else {
            this as FileEntry
        }
        FileEntryRepository.getInstance().resetDownloadDate(fileEntry)
    }


    companion object {
        fun getStorageFolder(storageType: StorageType, folder: String): String {
            return when (storageType) {
                StorageType.global -> GLOBAL_FOLDER
                StorageType.resource -> RESOURCE_FOLDER
                else -> folder
            }
        }
    }
}