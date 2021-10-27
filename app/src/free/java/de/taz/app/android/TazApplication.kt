package de.taz.app.android

import de.taz.app.android.download.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("UNUSED")
class TazApplication : AbstractTazApplication() {
    override fun onCreate() {
        super.onCreate()
        DownloadService.createInstance(applicationContext).apply {
            CoroutineScope(Dispatchers.IO).launch {
                scheduleNewestIssueDownload("poll/initial", true)
            }
        }
    }
}