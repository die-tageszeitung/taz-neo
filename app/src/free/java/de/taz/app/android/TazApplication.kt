package de.taz.app.android

import de.taz.app.android.data.DownloadScheduler

@Suppress("UNUSED")
class TazApplication : AbstractTazApplication() {

    override fun onCreate() {
        super.onCreate()
        DownloadScheduler.getInstance(applicationContext).ensurePollingWorkerIsScheduled()
   }
}