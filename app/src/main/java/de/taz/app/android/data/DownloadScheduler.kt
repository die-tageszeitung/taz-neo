package de.taz.app.android.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkRequest
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.download.ISSUE_DOWNLOAD_WORKER_POLL_TAG
import de.taz.app.android.download.IssueDownloadWorker
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Mockable
class DownloadScheduler(
    context: Context
) {
    companion object : SingletonHolder<DownloadScheduler, Context>(::DownloadScheduler)

    private val downloadDataStore = DownloadDataStore.getInstance(context)
    private val workerManager = WorkManager.getInstance(context)

    /**
     * get Constraints for [WorkRequest] of [WorkManager]
     */
    private suspend fun getBackgroundDownloadConstraints(): Constraints {
        val onlyWifi = downloadDataStore.onlyWifi.get()
        return Constraints.Builder()
            .setRequiredNetworkType(if (onlyWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
    }

    /**
     * download new issue in background
     */
    suspend fun scheduleNewestIssueDownload(
        tag: String,
        delay: Long = 0L
    ): Operation {
        val requestBuilder =
            OneTimeWorkRequest.Builder(IssueDownloadWorker::class.java)
                .setConstraints(getBackgroundDownloadConstraints())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(tag)

        return workerManager.enqueueUniqueWork(
            tag,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            requestBuilder.build()
        )
    }

    @Suppress("Unused") // this is used in the free build version
    public fun ensurePollingWorkerIsScheduled() {
        if(workerManager.getWorkInfosByTag(ISSUE_DOWNLOAD_WORKER_POLL_TAG).get().isEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                scheduleNewestIssueDownload(ISSUE_DOWNLOAD_WORKER_POLL_TAG)
            }
        }
    }
}