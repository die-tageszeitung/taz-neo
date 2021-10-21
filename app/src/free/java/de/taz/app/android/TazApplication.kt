package de.taz.app.android

import android.app.Application
import android.os.StrictMode
import com.facebook.stetho.Stetho
import de.taz.app.android.download.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("UNUSED")
class TazApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this)
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .penaltyLog()
                    .build()
            )
        }
        SentryProvider.initSentry(this)
        DownloadService.createInstance(applicationContext).apply {
            CoroutineScope(Dispatchers.IO).launch {
                scheduleNewestIssueDownload("poll/initial", true)
            }
        }
    }
}