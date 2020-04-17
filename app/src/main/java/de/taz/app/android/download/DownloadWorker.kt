package de.taz.app.android.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.models.Download
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.FileHelper
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

    constructor(applicationContext: Context) : this(
        okHttpClient(applicationContext),
        DownloadRepository.getInstance(applicationContext),
        FileEntryRepository.getInstance(applicationContext),
        FileHelper.createInstance(applicationContext),
        WorkManager.getInstance(applicationContext)
    )

    private val log by Log

    /**
     * start download of given files/downloads
     * @param fileEntries - [FileEntryOperations] to download
     * @param fileNames - [FileEntryOperations.name] of files to download
     * @param downloads - [Download]s to download
     */
    suspend fun startDownloads(
        fileEntries: List<FileEntryOperations>? = null,
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
     * @param fileName - [FileEntryOperations.name] of [FileEntryOperations] to download
     */
    suspend fun startDownload(fileName: String) {

        fileEntryRepository.get(fileName)?.let { fileEntry ->
            downloadRepository.getStub(fileName)?.let { fromDB ->
                // download only if not already downloaded or downloading
                if (fromDB.lastSha256 != fileEntry.sha256 || fromDB.status !in arrayOf(
                        DownloadStatus.done,
                        DownloadStatus.started
                    )
                ) {
                    log.debug("starting download of ${fromDB.fileName}")

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
                                if (sha256 == fileEntry.sha256) {
                                    log.debug("sha256 matched for file ${fromDB.fileName}")
                                    downloadRepository.saveLastSha256(fromDB, sha256)
                                    downloadRepository.setStatus(fromDB, DownloadStatus.done)
                                    log.debug("finished download of ${fromDB.fileName}")
                                } else {
                                    // TODO how to reload this?!
                                    log.warn("sha256 did NOT match the one of ${fromDB.fileName}")
                                    downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                                }
                            } ?: run {
                                log.debug("aborted download of ${fromDB.fileName} - file is empty")
                                downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                            }
                        } else {
                            log.warn("Download was not successful ${response.code}")
                            downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                            Sentry.capture(response.message)
                        }
                    } catch (e: Exception) {
                        log.warn("aborted download of ${fromDB.fileName} - ${e.localizedMessage}")
                        downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                        Sentry.capture(e)
                    }
                } else {
                    log.debug("skipping download of ${fromDB.fileName} - already downloading/ed")
                }

                // cancel workmanager request if downloaded successfully
                if (fromDB.status == DownloadStatus.done) {
                    fromDB.workerManagerId?.let {
                        workManager.cancelWorkById(it)
                        log.info("canceling WorkerManagerRequest for ${fromDB.fileName}")
                    }
                    fromDB.workerManagerId = null
                    downloadRepository.update(fromDB)
                }
            } ?: log.error("download for $fileName failed. File not found in downloadRepository")
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
    applicationContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(applicationContext, workerParameters) {

    override suspend fun doWork(): Result = coroutineScope {

        val downloadService = DownloadService.getInstance(applicationContext)
        val issueRepository = IssueRepository.getInstance(applicationContext)

        inputData.getString(DATA_ISSUE_FEEDNAME)?.let { feedName ->
            inputData.getString(DATA_ISSUE_DATE)?.let { date ->
                async {
                    try {
                        ApiService.getInstance(applicationContext).getIssueByFeedAndDate(
                            feedName,
                            date
                        )?.let { issue ->
                            issueRepository.apply {
                                save(issue)
                                setDownloadDate(issue, Date())
                            }
                            downloadService.scheduleDownload(issue)
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