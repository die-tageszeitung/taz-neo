package de.taz.app.android.api.interfaces

import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.GLOBAL_FOLDER
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.singletons.FileHelper

interface FileEntryOperations: CacheableDownload {
    val name: String
    val storageType: StorageType
    val moTime: Long
    val sha256: String
    val size: Long
    val folder: String
    override val downloadedStatus: DownloadStatus?

    val path
        get() = "$folder/$name"

    fun deleteFile() {
        val fileHelper = FileHelper.getInstance()
        // TODO check return value
        fileHelper.deleteFile(name)
        this.setDownloadStatus(DownloadStatus.pending)
        DownloadRepository.getInstance().delete(name)
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
    override fun getAllFileNames() = listOf(this.name)
}