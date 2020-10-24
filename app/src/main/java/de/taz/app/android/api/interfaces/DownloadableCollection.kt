package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.download.DownloadService
import java.util.*

/**
 * Interface every model has to implement which can be downloaded with [DownloadService]
 */
interface DownloadableCollection {
    val dateDownload: Date?

    suspend fun isDownloaded(): Boolean {
        return getDownloadDate() != null
    }

    suspend fun getDownloadDate(): Date?
    suspend fun setDownloadDate(date: Date?)

    fun getAllFiles(): List<FileEntry>
    fun getAllFileNames(): List<String>

    suspend fun deleteFiles()

    // the download tag can be used to cancel downloads
    fun getDownloadTag(): String

}