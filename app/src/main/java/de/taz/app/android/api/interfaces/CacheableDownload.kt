package de.taz.app.android.api.interfaces

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import kotlinx.coroutines.Job

/**
 * Interface every model has to implement which can be downloaded with [DownloadService]
 */
interface CacheableDownload {

    val downloadedStatus: DownloadStatus?

    /**
     * remove all downloaded files
     * metadata will be kepts
     */
    suspend fun deleteFiles(updateDatabase: Boolean = true) {
        if (updateDatabase) {
            setDownloadStatus(DownloadStatus.pending)
        }
        getAllFiles().forEach { it.deleteFile(updateDatabase) }
    }

    fun download(applicationContext: Context? = null): Job =
        DownloadService.getInstance(applicationContext).download(this@CacheableDownload)

    fun isDownloadedLiveData(applicationContext: Context?): LiveData<Boolean>

    suspend fun isDownloaded(applicationContext: Context?): Boolean {
        return getDownloadedStatus(applicationContext)?.equals(DownloadStatus.done) ?: run {
            val isDownloadedInDb = isDownloadedInDb(applicationContext)
            val downloadedStatusFromDb =
                if (isDownloadedInDb) DownloadStatus.done else DownloadStatus.pending
            setDownloadStatus(downloadedStatusFromDb)
            isDownloadedInDb
        }
    }

    fun getLiveData(applicationContext: Context?): LiveData<out CacheableDownload?>
    fun getDownloadedStatus(applicationContext: Context?): DownloadStatus?

    fun setDownloadStatus(downloadStatus: DownloadStatus)

    private fun isDownloadedInDb(applicationContext: Context?): Boolean {
        return DownloadRepository.getInstance(applicationContext).isDownloaded(getAllFileNames())
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
    fun getIssueOperations(applicationContext: Context?): IssueOperations? = null
}