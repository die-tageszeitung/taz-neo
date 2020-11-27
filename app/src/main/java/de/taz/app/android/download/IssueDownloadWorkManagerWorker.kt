package de.taz.app.android.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.taz.app.android.DISPLAYED_FEED
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.util.NewIssuePollingScheduler
import de.taz.app.android.data.DataService
import de.taz.app.android.util.Log
import io.sentry.core.Sentry
import kotlinx.coroutines.*

const val KEY_SCHEDULE_NEXT = "KEY_SCHEDULE_NEXT"


class IssueDownloadWorkManagerWorker(
    applicationContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(applicationContext, workerParameters) {

    private val log by Log

    override suspend fun doWork(): Result = coroutineScope {
        val dataService = DataService.getInstance(applicationContext)
        val downloadService = DownloadService.getInstance(applicationContext)
        val oldFeed = dataService.getFeedByName(DISPLAYED_FEED)

        val scheduleNext = inputData.getBoolean(KEY_SCHEDULE_NEXT, false)

        if (scheduleNext) {
            val nextPollDelay = NewIssuePollingScheduler.getDelayForNextPoll()
            downloadService.scheduleNewestIssueDownload(
                "poll/$nextPollDelay",
                true,
                nextPollDelay
            )
        }

        try {
            // maybe get new issue
            val newsestIssue = dataService.refreshFeedAndGetIssueIfNew(DISPLAYED_FEED)
            if (newsestIssue == null) {
                log.info("No new issue found, newest issue: ${oldFeed?.publicationDates?.getOrNull(0)}")
                return@coroutineScope Result.success()
            }
            dataService.ensureDownloaded(newsestIssue)
            // pre download moment too
            val moment = dataService.getMoment(newsestIssue.issueKey) ?: run {
                val hint = "Did not find moment for issue at ${newsestIssue.date}"
                Sentry.captureMessage(hint)
                log.error(hint)
                return@coroutineScope Result.failure()
            }
            dataService.ensureDownloaded(moment)

            log.info("Downloaded new issue automatically: ${newsestIssue.date}")
            return@coroutineScope Result.success()
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
