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
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.ui.splash.ResourceInitUtil
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
        val downloadDataStore = DownloadDataStore.getInstance(applicationContext)

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
            // Ensure the default nav button exists, as it is required when saving a [Section] as part of an [Issue]
            ResourceInitUtil(applicationContext).apply {
                ensureDefaultNavButtonExists()
            }

            // maybe get new issue
            val newestIssueKey =
                feedService.refreshFeedAndGetIssueKeyIfNew(DISPLAYED_FEED)
            if (newestIssueKey == null) {
                val oldFeed = feedService.getFeedFlowByName(DISPLAYED_FEED).first()
                log.info("No new issue found, newest issue: ${oldFeed?.publicationDates?.getOrNull(0)?.date}")
                return@coroutineScope Result.success()
            } else {
                if (downloadDataStore.pdfAdditionally.get()) {
                    val issuePublicationWithPages = IssuePublicationWithPages(newestIssueKey)
                    contentService.downloadToCache(issuePublicationWithPages, isAutomaticDownload = true)
                } else {
                    val issuePublication = IssuePublication(newestIssueKey)
                    contentService.downloadToCache(issuePublication, isAutomaticDownload = true)
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
