package de.taz.app.android.api.interfaces

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Interface every model has to implement which can be downloaded with [DownloadService]
 */
interface CacheableDownload {

    /**
     * remove all downloaded files
     * metadata will be kepts
     */
    suspend fun deleteFiles() {
        getAllFiles().forEach { it.deleteFile() }
    }

    suspend fun download(applicationContext: Context? = null) = withContext(Dispatchers.IO) {
        DownloadService.getInstance(applicationContext).download(this@CacheableDownload)
    }

    fun isDownloadedLiveData(): LiveData<Boolean> {
        return DownloadRepository.getInstance().isDownloadedLiveData(getAllFileNames())
    }

    fun isDownloaded(): Boolean {
        return DownloadRepository.getInstance().isDownloaded(getAllFileNames())
    }

    fun isDownloadedOrDownloading(): Boolean {
        return DownloadRepository.getInstance().isDownloadedOrDownloading(getAllFileNames())
    }

    fun isDownloadedOrDownloadingLiveData(): LiveData<Boolean> {
        return DownloadRepository.getInstance().isDownloadingOrDownloadedLiveData(getAllFileNames())
    }

    fun getAllFileNames(): List<String>
    suspend fun getAllFiles(): List<FileEntryOperations> {
        val fileEntryRepository = FileEntryRepository.getInstance()
        return getAllFileNames().mapNotNull { fileEntryRepository.get(it) }
    }

    // the download tag can be used to cancel downloads
    fun getDownloadTag(): String? = null

    /**
     * if the [CacheableDownload] has [FileEntry]s of [StorageType.issue] we need the
     * [IssueOperations] for baseUrl and tag
     */
    fun getIssueOperations(): IssueOperations? = null
}