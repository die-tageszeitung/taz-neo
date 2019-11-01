package de.taz.app.android.api.interfaces

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Issue
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.DownloadRepository

/**
 * Interface every model has to implement which can be downloaded with [DownloadService]
 */
interface CacheableDownload {

    fun download(applicationContext: Context) {
        DownloadService.download(applicationContext, this)
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

    fun getAllFileNames(): List<String> {
        return getAllFiles().map { it.name }
    }

    fun getAllFiles(): List<FileEntry>

    // the download tag can be used to cancel downloads
    fun getDownloadTag(): String? = null

    /**
     * if the [CacheableDownload] has [FileEntry]s of [StorageType.issue] we need the
     * [IssueOperations] for baseUrl and tag
     */
    fun getIssueOperations(): IssueOperations? = null
}