package de.taz.app.android.download

import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.taz.app.android.api.models.Download
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.util.Log
import de.taz.app.android.util.awaitCallback
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest


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
                    DownloadWorker.startDownload(appContext, fileName)
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

/**
 * Helper Object used by [WorkManagerDownloadWorker] and [DownloadService] to download
 */
object DownloadWorker {

    private val log by Log
    private val httpClient: OkHttpClient = getHttpClient()

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
        val downloadRepository = DownloadRepository(AppDatabase.createInstance(appContext))

        downloadRepository.get(fileName)?.let { fromDB ->
            // download only if not already downloaded
            if (fromDB.status != DownloadStatus.done) {
                log.debug("starting download of ${fromDB.file.name}")

                downloadRepository.setStatus(fromDB, DownloadStatus.started)

                val response = awaitCallback(
                    httpClient.newCall(
                        Request.Builder().url(fromDB.url).get().build()
                    )::enqueue
                )

                val file = getFile(appContext, fromDB.path)
                response.body?.bytes()?.let { bytes ->
                    // ensure folders are created
                    getFile(appContext, fromDB.folder).mkdirs()
                    file.writeBytes(bytes)

                    // check sha256
                    val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                        .fold("", { str, it -> str + "%02x".format(it) })
                    if (sha256 == fromDB.file.sha256) {
                        log.info("sha256 matched for file ${fromDB.file.name}")
                    } else {
                        // TODO handle wrong sha256
                        log.warn("sha256 did NOT match the one of ${fromDB.file.name}")
                    }
                }
                log.debug("finished download of ${fromDB.file.name}")
            } else {
                log.debug("skipping download of ${fromDB.file.name} as it was already finished")
            }

            // cancel workmanager request if one exists
            fromDB.workerManagerId?.let { WorkManager.getInstance(appContext).cancelWorkById(it) }

            // update download and save
            fromDB.workerManagerId = null
            fromDB.status = DownloadStatus.done
            downloadRepository.update(fromDB)
        } ?: log.error("download $fileName not found")
    }

    private fun getFile(appContext: Context, fileName: String, internal: Boolean = false): File {
        // TODO read from settings where to save
        // TODO notification if external not writable?
        return if (internal || !isExternalStorageWritable())
            File(appContext.filesDir, fileName)
        else {
            return File(ContextCompat.getExternalFilesDirs(appContext, null).first(), fileName)
        }
    }

    /* Checks if external storage is available for read and write */
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

}

private fun getHttpClient(): OkHttpClient {
    return OkHttpClient.Builder().build()
}
