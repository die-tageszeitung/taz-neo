package de.taz.app.android.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.taz.app.android.DISPLAYABLE_NAME
import de.taz.app.android.DISPLAYED_FEED
import de.taz.app.android.data.DataService
import de.taz.app.android.util.Log
import io.sentry.core.Sentry
import kotlinx.coroutines.*


class IssueDownloadWorkManagerWorker(
    applicationContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(applicationContext, workerParameters) {

    private val log by Log

    override suspend fun doWork(): Result = coroutineScope {
        val dataService = DataService.getInstance(applicationContext)
        val oldFeed = dataService.getFeedByName(DISPLAYABLE_NAME)
        val feed = dataService.getFeedByName(DISPLAYED_FEED, allowCache = false)
        try {
            // determine whether a new issue was published
            if (oldFeed?.publicationDates?.get(0) != feed?.publicationDates?.get(0)) {
                // download that new issue
                feed?.let {
                    val issueStub = dataService.getIssueStubsByFeed(
                        it.publicationDates[0],
                        listOf(it.name),
                        1,
                        allowCache = false
                    ).firstOrNull()
                    val issue = issueStub?.getIssue()
                    issue?.let { issue ->
                        dataService.ensureDownloaded(issue)
                        log.info("Downloaded new issue automatically: ${issueStub.getDownloadDate()}")
                        Result.success()
                    }?.run {
                        log.error("Expected issue ${issueStub.getDownloadDate()} not Fount")
                        Result.failure()
                    }
                } ?: run {
                    val hint = "feed $DISPLAYED_FEED not found"
                    log.error(hint)
                    Sentry.captureMessage(hint)
                    Result.failure()
                }
            } else {
                log.info("No new issue found, last issue is ${oldFeed?.publicationDates?.get(0)}")
                Result.success()
            }
        } catch (e: Exception) {
            log.error("Error during automatic download")
            Sentry.captureException(e)
            Result.failure()
        }
    }
}
