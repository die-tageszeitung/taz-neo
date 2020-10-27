package de.taz.app.android.api.interfaces

import java.util.*


interface DownloadableStub {
    val dateDownload: Date?

    suspend fun isDownloaded(): Boolean {
        return getDownloadDate() != null
    }

    suspend fun getDownloadDate(): Date?
    suspend fun setDownloadDate(date: Date?)

    // the download tag can be used to cancel downloads
    fun getDownloadTag(): String

}