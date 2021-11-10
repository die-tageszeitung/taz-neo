package de.taz.app.android.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.taz.app.android.DISPLAYED_FEED
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.content.ContentService
import de.taz.app.android.util.NewIssuePollingScheduler
import de.taz.app.android.data.DataService
import de.taz.app.android.data.DownloadScheduler
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import io.sentry.Sentry
import kotlinx.coroutines.*

const val KEY_SCHEDULE_NEXT = "KEY_SCHEDULE_NEXT"


class IssueDownloadWorkManagerWorker(
    applicationContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(applicationContext, workerParameters) {

    private val log by Log

    private val downloadScheduler = DownloadScheduler.getInstance(applicationContext)

    override suspend fun doWork(): Result = coroutineScope {
        val dataService = withContext(Dispatchers.Main) {
            DataService.getInstance(applicationContext)
        }
        val contentService = ContentService.getInstance(applicationContext)
        val oldFeed = dataService.getFeedByName(DISPLAYED_FEED)

        val scheduleNext = inputData.getBoolean(KEY_SCHEDULE_NEXT, false)

        if (scheduleNext) {
            val nextPollDelay = NewIssuePollingScheduler.getDelayForNextPoll()
            downloadScheduler.scheduleNewestIssueDownload(
                "poll/$nextPollDelay",
                true,
                nextPollDelay
            )
        }

        try {
            // maybe get new issue
            val newestIssueKey = dataService.refreshFeedAndGetIssueKeyIfNew(DISPLAYED_FEED)
            if (newestIssueKey == null) {
                log.info("No new issue found, newest issue: ${oldFeed?.publicationDates?.getOrNull(0)}")
                return@coroutineScope Result.success()
            } else {
                contentService.downloadToCacheIfNotPresent(newestIssueKey, isAutomaticDownload = true)
                log.info("Downloaded new issue automatically: ${newestIssueKey.date}")
                return@coroutineScope Result.success()
            }
        } catch (e: ConnectivityException.Recoverable) {
            log.warn("Connection Failure while trying to retrieve new issue")
            return@coroutineScope Result.failure()
        } catch (e: Exception) {
            log.error("Error during automatic download")
            Sentry.captureException(e)
            return@coroutineScope Result.failure()
        }
    }
}
