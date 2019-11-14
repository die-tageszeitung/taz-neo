package de.taz.app.android.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Download
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.awaitCallback
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest


/**
 * Helper Object used by [WorkManagerDownloadWorker] and [DownloadService] to download
 */
class DownloadWorker(val httpClient: OkHttpClient) {

    private val log by Log

    /**
     * start download of given files/downloads
     * @param fileEntries - [FileEntry]s to download
     * @param fileNames - [FileEntry.name] of files to download
     * @param downloads - [Download]s to download
     */
    suspend fun startDownloads(
        appContext: Context,
        fileEntries: List<FileEntry>? = null,
        fileNames: List<String>? = null,
        downloads: List<Download>? = null
    ) {
        fileNames?.forEach {
            startDownload(appContext, it)
        }
        downloads?.forEach {
            startDownload(appContext, it.file.name)
        }
        fileEntries?.forEach {
            startDownload(appContext, it.name)
        }
    }

    /**
     * start download
     * @param fileName - [FileEntry.name] of [FileEntry] to download
     */
    suspend fun startDownload(appContext: Context, fileName: String) {
        val downloadRepository = DownloadRepository.getInstance(appContext)
        val fileHelper = FileHelper.createInstance(appContext)

        downloadRepository.get(fileName)?.let { fromDB ->
            // download only if not already downloaded
            if (fromDB.status != DownloadStatus.done) {
                log.debug("starting download of ${fromDB.file.name}")

                downloadRepository.setStatus(fromDB, DownloadStatus.started)

                try {
                    val response = awaitCallback(
                        httpClient.newCall(
                            Request.Builder().url(fromDB.url).get().build()
                        )::enqueue
                    )

                    val file = fileHelper.getFile(fromDB.path)
                    response.body?.bytes()?.let { bytes ->
                        // ensure folders are created
                        fileHelper.getFile(fromDB.folder).mkdirs()
                        file.writeBytes(bytes)

                        // check sha256
                        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                            .fold("", { str, it -> str + "%02x".format(it) })
                        if (sha256 == fromDB.file.sha256) {
                            log.info("sha256 matched for file ${fromDB.file.name}")
                        } else {
                            log.warn("sha256 did NOT match the one of ${fromDB.file.name}")
                        }
                    }
                    log.debug("finished download of ${fromDB.file.name}")
                } catch (e: Exception) {
                    downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                }
            } else {
                log.debug("skipping download of ${fromDB.file.name} as it was already finished")
            }


            // update download and save
            // cancel workmanager request if one exists
            fromDB.workerManagerId?.let {
                WorkManager.getInstance(appContext).cancelWorkById(it)
                fromDB.workerManagerId = null
                log.info("canceling WorkerManagerRequest for ${fromDB.file.name}")
            }
            fromDB.status = DownloadStatus.done
            downloadRepository.update(fromDB)
        } ?: log.error("download $fileName not found")
    }

}

/**
 * [CoroutineWorker] to be used with [androidx.work.WorkManager]
 */
class WorkManagerDownloadWorker(
    private val appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    private val log by Log

    override suspend fun doWork(): Result = coroutineScope {
        inputData.getString(DATA_DOWNLOAD_FILE_NAME)?.let { fileName ->
            async {
                try {
                    DownloadWorker(OkHttpClient()).startDownload(appContext, fileName)
                    log.debug("download of $fileName succeeded")
                    Result.success()
                } catch (ioe: IOException) {
                    log.error("download of $fileName failed", ioe)
                    Result.failure()
                }
            }
        }?.await() ?: Result.failure()
    }
}

class ScheduleIssueDownloadWorkManagerWorker(
    private val appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result = coroutineScope {
        inputData.getString(DATA_ISSUE_FEEDNAME)?.let { feedName ->
            inputData.getString(DATA_ISSUE_DATE)?.let { date ->
                async {
                    try {
                        ApiService.getInstance(appContext).getIssueByFeedAndDate(feedName, date).let { issue ->
                            IssueRepository.getInstance(appContext).save(issue)
                            DownloadService.scheduleDownload(appContext, issue)
                            Result.success()
                        }
                    } catch (e: Exception) {
                        Result.failure()
                    }
                }
            }
        }?.await() ?: Result.failure()
    }
}