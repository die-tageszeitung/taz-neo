package de.taz.app.android

import android.os.StrictMode
import com.facebook.stetho.Stetho
import de.taz.app.android.data.DownloadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("UNUSED")
class TazApplication : AbstractTazApplication() {
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
        DownloadScheduler(applicationContext).apply {
            CoroutineScope(Dispatchers.IO).launch {
                scheduleNewestIssueDownload("poll/initial", true)
            }
        }
    }
}