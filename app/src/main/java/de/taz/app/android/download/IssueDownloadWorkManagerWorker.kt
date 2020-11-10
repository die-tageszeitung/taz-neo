package de.taz.app.android.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.taz.app.android.DISPLAYED_FEED
import de.taz.app.android.util.NewIssuePollingScheduler
import de.taz.app.android.data.DataService
import de.taz.app.android.util.Log
import io.sentry.core.Sentry
import kotlinx.coroutines.*
import java.util.*

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
            downloadService.scheduleNewestIssueDownload("poll/${Date().time}/$nextPollDelay", true, nextPollDelay)
        }

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
                        val moment = dataService.getMoment(issue.issueKey)
                        dataService.ensureDownloaded(moment!!)
                        log.info("Downloaded new issue automatically: ${issueStub.getDownloadDate()}")
                        return@coroutineScope Result.success()
                    } ?: run {
                        log.error("Expected issue ${it.publicationDates[0]} not found")
                        return@coroutineScope Result.failure()
                    }
                } ?: run {
                    val hint = "feed $DISPLAYED_FEED not found"
                    log.error(hint)
                    Sentry.captureMessage(hint)
                    return@coroutineScope Result.failure()
                }
            } else {
                log.info("No new issue found, last issue is ${oldFeed?.publicationDates?.get(0)}")
                return@coroutineScope Result.success()
            }
        } catch (e: Exception) {
            log.error("Error during automatic download")
            Sentry.captureException(e)
            return@coroutineScope Result.failure()
        }
    }
}
