package de.taz.app.android.api.interfaces

import android.content.Context
import java.util.*


interface DownloadableStub: ObservableDownload {
    val dateDownload: Date?

    suspend fun isDownloaded(applicationContext: Context): Boolean {
        return getDownloadDate(applicationContext) != null
    }

    fun getDownloadDate(applicationContext: Context): Date?
    fun setDownloadDate(date: Date?, applicationContext: Context)

}

interface ObservableDownload {

    // the download tag can be used to cancel downloads
    fun getDownloadTag(): String
}