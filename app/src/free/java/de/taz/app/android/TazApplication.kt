package de.taz.app.android

import de.taz.app.android.data.DownloadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("UNUSED")
class TazApplication : AbstractTazApplication() {
    var elapsedPopupAlreadyShown = false
    override fun onCreate() {
        super.onCreate()
        DownloadScheduler(applicationContext).apply {
            CoroutineScope(Dispatchers.IO).launch {
                scheduleNewestIssueDownload("poll/initial", true)
            }
        }
    }
}