package de.taz.app.android.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.download.IssueDownloadWorkManagerWorker
import de.taz.app.android.download.KEY_SCHEDULE_NEXT
import de.taz.app.android.util.SingletonHolder
import java.util.concurrent.TimeUnit

@Mockable
class DownloadScheduler(
    private val context: Context
) {
    companion object : SingletonHolder<DownloadScheduler, Context>(::DownloadScheduler)

    private val downloadDataStore = DownloadDataStore.getInstance(context)

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
        polling: Boolean = false,
        delay: Long = 0L
    ): Operation {
        val requestBuilder =
            OneTimeWorkRequest.Builder(IssueDownloadWorkManagerWorker::class.java)
                .setConstraints(getBackgroundDownloadConstraints())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_SCHEDULE_NEXT to polling
                    )
                )
                .addTag(tag)

        return WorkManager.getInstance(context).enqueueUniqueWork(
            tag,
            ExistingWorkPolicy.KEEP,
            requestBuilder.build()
        )
    }
}