package de.taz.app.android.download

import android.content.Context
import androidx.annotation.UiThread
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Download
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.awaitCallback
import de.taz.app.android.util.okHttpClient
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.util.*


/**
 * Helper Object used by [WorkManagerDownloadWorker] and [DownloadService] to download
 */

class DownloadWorker(
    private val httpClient: OkHttpClient,
    private val downloadRepository: DownloadRepository,
    private val fileEntryRepository: FileEntryRepository,
    private val fileHelper: FileHelper,
    private val workManager: WorkManager
) {

    constructor(appContext: Context) : this(
        okHttpClient,
        DownloadRepository.getInstance(appContext),
        FileEntryRepository.getInstance(appContext),
        FileHelper.createInstance(appContext),
        WorkManager.getInstance(appContext)
    )

    private val log by Log

    /**
     * start download of given files/downloads
     * @param fileEntries - [FileEntry]s to download
     * @param fileNames - [FileEntry.name] of files to download
     * @param downloads - [Download]s to download
     */
    suspend fun startDownloads(
        fileEntries: List<FileEntry>? = null,
        fileNames: List<String>? = null,
        downloads: List<Download>? = null
    ) {
        fileNames?.forEach {
            startDownload(it)
        }
        downloads?.forEach {
            startDownload(it.file.name)
        }
        fileEntries?.forEach {
            startDownload(it.name)
        }
    }

    /**
     * start download
     * @param fileName - [FileEntry.name] of [FileEntry] to download
     */
    @UiThread
    suspend fun startDownload(fileName: String) {

        fileEntryRepository.get(fileName)?.let { fileEntry ->
            downloadRepository.get(fileName)?.let { fromDB ->
                // download only if not already downloaded or downloading
                if (fromDB.status !in arrayOf(DownloadStatus.done, DownloadStatus.started)) {
                    log.debug("starting download of ${fromDB.file.name}")

                    downloadRepository.setStatus(fromDB, DownloadStatus.started)

                    try {
                        val response = awaitCallback(
                            httpClient.newCall(
                                Request.Builder().url(fromDB.url).get().build()
                            )::enqueue
                        )

                        if (response.code.toString().startsWith("2")) {
                            val bytes = withContext(Dispatchers.IO) { response.body?.bytes() }
                            @Suppress("NAME_SHADOWING")
                            bytes?.let { bytes ->
                                // ensure folders are created
                                fileHelper.createFileDirs(fileEntry)
                                fileHelper.writeFile(fileEntry, bytes)

                                // check sha256
                                val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                                    .fold("", { str, it -> str + "%02x".format(it) })
                                if (sha256 == fromDB.file.sha256) {
                                    log.debug("sha256 matched for file ${fromDB.file.name}")
                                    downloadRepository.setStatus(fromDB, DownloadStatus.done)
                                    log.debug("finished download of ${fromDB.file.name}")
                                } else {
                                    log.warn("sha256 did NOT match the one of ${fromDB.file.name}")
                                    downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                                }
                            } ?: run {
                                log.debug("aborted download of ${fromDB.file.name} - file is empty")
                                downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                            }
                        } else {
                            log.warn("Download was not successful")
                            downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                            Sentry.capture(response.message)
                        }
                    } catch (e: Exception) {
                        log.debug("aborted download of ${fromDB.file.name} - ${e.localizedMessage}")
                        downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                        Sentry.capture(e)
                    }
                } else {
                    log.debug("skipping download of ${fromDB.file.name} - already downloading/ed")
                }

                // cancel workmanager request if downloaded successfully
                if (fromDB.status == DownloadStatus.done) {
                    fromDB.workerManagerId?.let {
                        workManager.cancelWorkById(it)
                        log.info("canceling WorkerManagerRequest for ${fromDB.file.name}")
                    }
                    fromDB.workerManagerId = null
                    downloadRepository.update(fromDB)
                }
            } ?: log.error("download for $fileName not found")
        }
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
                    DownloadWorker(appContext).startDownload(fileName)
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
                        ApiService.getInstance(appContext).getIssueByFeedAndDate(
                            feedName,
                            date
                        )?.let { issue ->
                            IssueRepository.getInstance(appContext).save(issue)
                            IssueRepository.getInstance(appContext).setDownloadDate(issue, Date())
                            DownloadService.scheduleDownload(appContext, issue)
                            Result.success()
                        } ?: Result.failure()
                    } catch (e: Exception) {
                        Sentry.capture(e)
                        Result.failure()
                    }
                }
            }
        }?.await() ?: Result.failure()
    }
}