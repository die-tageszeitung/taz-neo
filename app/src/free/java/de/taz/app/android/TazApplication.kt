package de.taz.app.android

import android.app.Application
import com.facebook.stetho.Stetho
import de.taz.app.android.download.DownloadService

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
        }
        SentryProvider.initSentry(this)
        DownloadService.createInstance(applicationContext).apply {
            scheduleNewestIssueDownload("poll/initial", true)
        }
    }
}