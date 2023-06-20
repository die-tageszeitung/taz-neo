package de.taz.app.android

import de.taz.app.android.data.DownloadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import de.taz.app.android.tracking.NoOpTracker
import de.taz.app.android.tracking.Tracker

@Suppress("UNUSED")
class TazApplication : AbstractTazApplication() {

    override fun onCreate() {
        super.onCreate()
        DownloadScheduler(applicationContext).apply {
            CoroutineScope(Dispatchers.IO).launch {
                scheduleNewestIssueDownload("poll/initial", true)
            }
        }
    }

    override fun initializeTracker(): Tracker {
        return NoOpTracker()
    }
}