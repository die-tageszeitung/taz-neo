package de.taz.app.android.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.taz.app.android.BuildConfig.DISPLAYED_FEED
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.FeedService
import de.taz.app.android.data.DownloadScheduler
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.ui.splash.ResourceInitUtil
import de.taz.app.android.util.Log
import de.taz.app.android.util.NewIssuePollingScheduler
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

const val ISSUE_DOWNLOAD_WORKER_POLL_TAG = "ISSUE_DOWNLOAD_WORKER_POLL_TAG"
const val ISSUE_DOWNLOAD_WORKER_FIREBASE_TAG = "ISSUE_DOWNLOAD_WORKER_FIREBASE_TAG"

class IssueDownloadWorker(
    applicationContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(applicationContext, workerParameters) {

    private val log by Log

    private val downloadScheduler = DownloadScheduler.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)
    private val feedService = FeedService.getInstance(applicationContext)
    private val downloadDataStore = DownloadDataStore.getInstance(applicationContext)
    private val resourceInitUtil = ResourceInitUtil(applicationContext)

    override suspend fun doWork(): Result = coroutineScope {
        try {
            // Ensure the default nav button exists, as it is required when saving a [Section] as part of an [Issue]
            resourceInitUtil.ensureDefaultNavButtonExists()

            downloadNewestIssue()

            if (ISSUE_DOWNLOAD_WORKER_POLL_TAG in tags) {
                scheduleNextIssueDownload()
            }

            return@coroutineScope Result.success()
        } catch (e: ConnectivityException.Recoverable) {
            SentryWrapper.captureException(e)
            log.warn("Connection Failure while trying to retrieve new issue")
            return@coroutineScope Result.retry()
        } catch (e: Exception) {
            log.error("Error during automatic download")
            SentryWrapper.captureException(e)
            return@coroutineScope Result.failure()
        }
    }

    private suspend fun scheduleNextIssueDownload() {
        val nextPollDelay = NewIssuePollingScheduler.getDelayForNextPoll()
        downloadScheduler.scheduleNewestIssueDownload(
            ISSUE_DOWNLOAD_WORKER_POLL_TAG,
            nextPollDelay
        )
    }

    private suspend fun downloadNewestIssue() {
        // get the latest date we know about before refreshing
        val oldFeed = feedService.getFeedFlowByName(DISPLAYED_FEED).first()
        val oldNewestIssueDate = oldFeed?.publicationDates?.getOrNull(0)?.date

        // update feed and newest issue metadata
        val newestIssueDate = feedService.refreshFeedAndGetIssueKeyIfNew(DISPLAYED_FEED)?.date

        // if user does not want to download the issue return
        if (!downloadDataStore.enabled.get())
            return

        // if there is no issue we did not know about
        if (newestIssueDate == null) {
            log.info("No new issue found, newest issue: $oldNewestIssueDate")

            // we are polling do not return
            // as downloading the issue we knew about before updating might be one
            // the user already deleted before
            if (ISSUE_DOWNLOAD_WORKER_POLL_TAG in tags) {
                return
            }
        }

        // either download the new issue or the newest we know about before
        val downloadDate = newestIssueDate ?: oldNewestIssueDate

        // if we have no issue to download return
        if (downloadDate == null) {
            return
        }

        contentService.downloadIssuePublicationToCache(
            IssuePublication(DISPLAYED_FEED, simpleDateFormat.format(downloadDate))
        )
        log.info("Downloaded new issue automatically: $downloadDate")
    }
}
