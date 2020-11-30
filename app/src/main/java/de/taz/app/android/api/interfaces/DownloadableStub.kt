package de.taz.app.android.api.interfaces

import android.content.Context
import java.util.*


interface DownloadableStub: ObservableDownload {
    val dateDownload: Date?

    suspend fun isDownloaded(): Boolean {
        return getDownloadDate() != null
    }

    fun getDownloadDate(context: Context? = null): Date?
    fun setDownloadDate(date: Date?, context: Context? = null)


}

interface ObservableDownload {

    // the download tag can be used to cancel downloads
    fun getDownloadTag(): String
}