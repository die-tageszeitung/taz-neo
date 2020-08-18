package de.taz.app.android.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import io.sentry.Sentry
import kotlinx.coroutines.*


class IssueDownloadWorkManagerWorker(
    applicationContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(applicationContext, workerParameters) {

    private val log by Log

    override suspend fun doWork(): Result = coroutineScope {

        val issueFeedName = inputData.getString(DATA_ISSUE_FEEDNAME)
        val issueDate = inputData.getString(DATA_ISSUE_DATE)
        val issueStatus = inputData.getString(DATA_ISSUE_STATUS)?.let { IssueStatus.valueOf(it) }

        log.debug("starting to download - issueDate: $issueDate issueFeedName: $issueFeedName issueStatus: $issueStatus")

        runIfNotNull(issueFeedName, issueDate, issueStatus) { feedName, date, status ->
            val downloadService = DownloadService.getInstance(applicationContext)
            val issueRepository = IssueRepository.getInstance(applicationContext)

            return@runIfNotNull try {
                issueRepository.getIssue(
                    feedName, date, status
                )?.let { issue ->
                    runBlocking { downloadService.download(issue).join() }
                    log.debug("successfully downloaded")
                    Result.success()
                } ?: Result.failure()
            } catch (e: Exception) {
                log.debug("download failed")
                Sentry.capture(e)
                Result.failure()
            }
        } ?: run {
            Sentry.capture("download failed - not enough information - issueDate: $issueDate issueFeedName: $issueFeedName issueStatus: $issueStatus")
            Result.failure()
        }
    }
}
