package de.taz.app.android.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.FeedService
import de.taz.app.android.data.DownloadScheduler
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.persistence.repository.IssueKeyWithPages
import de.taz.app.android.util.Log
import de.taz.app.android.util.NewIssuePollingScheduler
import io.sentry.Sentry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

const val KEY_SCHEDULE_NEXT = "KEY_SCHEDULE_NEXT"


class IssueDownloadWorkManagerWorker(
    applicationContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(applicationContext, workerParameters) {

    private val log by Log

    private val downloadScheduler = DownloadScheduler.getInstance(applicationContext)


    override suspend fun doWork(): Result = coroutineScope {
        val contentService = ContentService.getInstance(applicationContext)
        val feedService = FeedService.getInstance(applicationContext)

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
            val newestIssueKey =
                feedService.refreshFeedAndGetIssueKeyIfNew(BuildConfig.DISPLAYED_FEED)
            if (newestIssueKey == null) {
                val oldFeed = feedService.getFeedFlowByName(BuildConfig.DISPLAYED_FEED).first()
                log.info("No new issue found, newest issue: ${oldFeed?.publicationDates?.getOrNull(0)}")
                return@coroutineScope Result.success()
            } else {
                if (DownloadDataStore.getInstance(applicationContext).pdfAdditionally.get()) {
                    val issueKeyWithPages = IssueKeyWithPages(newestIssueKey)
                    contentService.downloadToCache(issueKeyWithPages, isAutomaticDownload = true)
                } else {
                    contentService.downloadToCache(newestIssueKey, isAutomaticDownload = true)
                }
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
