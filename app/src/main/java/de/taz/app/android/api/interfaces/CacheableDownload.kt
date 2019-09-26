package de.taz.app.android.api.interfaces

import androidx.lifecycle.LiveData
import de.taz.app.android.persistence.repository.DownloadRepository

interface CacheableDownload {

    fun isDownloadedLiveData(): LiveData<Boolean> {
        return DownloadRepository.getInstance().isDownloadedLiveData(getAllFileNames())
    }


    fun isDownloaded(): Boolean {
        return DownloadRepository.getInstance().isDownloaded(getAllFileNames())
    }

    fun getAllFileNames(): List<String>
}