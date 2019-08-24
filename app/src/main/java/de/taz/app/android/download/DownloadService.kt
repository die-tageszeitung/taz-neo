package de.taz.app.android.download

import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.common.util.concurrent.ListenableFuture
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import de.taz.app.android.util.awaitCallback
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

const val ACTION_DOWNLOAD = "action.download"
const val DATA_DOWNLOAD = "extra.download"
const val RECEIVER = "receiver"

const val RESOURCE_FOLDER = "resources"


object DownloadService {

    val apiService = ApiService()

    fun downloadResources(appContext: Context, resourceInfo: ResourceInfo) {
        enqueueDownloads(appContext, resourceInfo.resourceList.map { Download(resourceInfo.resourceBaseUrl, RESOURCE_FOLDER, it) })
    }

    fun downloadIssue(appContext: Context, issue: Issue) {
        enqueueDownloads(
            appContext,
            issue.fileList.filter { !it.startsWith("/global") }.map { Download(issue.baseUrl, issue.date, it) }
        )
    }


    private fun enqueueDownloads(appContext: Context, downloads: List<Download>): Operation {
        return WorkManager.getInstance(appContext).enqueue(downloads.map { createRequest(it) })
    }

    private fun enqueueDownload(appContext: Context, download: Download): Operation {
        return WorkManager.getInstance(appContext).enqueue(createRequest(download))
    }

    private fun createRequest(download: Download): OneTimeWorkRequest {
        val data = Data.Builder().putString(DATA_DOWNLOAD, download.serialize()).build()
        // TODO add constraints
        val constraints = Constraints.Builder().build()

        return OneTimeWorkRequest.Builder(DownloadWorker::class.java)
            .setInputData(data)
            .setConstraints(constraints)
            .build()
    }
}

class DownloadWorker(private val appContext: Context, workerParameters: WorkerParameters): CoroutineWorker(appContext, workerParameters) {
    private val log by Log
    private val httpClient: OkHttpClient = getHttpClient()

    override suspend fun doWork(): Result = coroutineScope {
        val serializedDownload = inputData.getString(DATA_DOWNLOAD)
        serializedDownload?.let {
            val download = Download.deserialize(it)
            async {
                try {
                    startDownload(appContext, download)
                    log.debug("download of ${download.name} succeeded")
                    Result.success()
                } catch (ioe: IOException) {
                    log.error("download of ${download.name} failed", ioe)
                    Result.failure()
                }
            }
        }?.await() ?: Result.failure()
    }

    private suspend fun startDownload(appContext: Context, download: Download) {
        val response = awaitCallback(httpClient.newCall(
            Request.Builder().url(download.url).get().build()
        )::enqueue)

        // TODO check if file already exists and needs updating
        val file = getFile(appContext, download.path)
        response.body?.bytes()?.let {
            // ensure folders are created
            getFile(appContext, download.folder).mkdirs()
            file.writeBytes(it)
        }
        // TODO verifyDownload(download) in second service!?
    }

    /* Checks if external storage is available for read and write */
    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    /* Checks if external storage is available to at least read */
    fun isExternalStorageReadable(): Boolean {
        return Environment.getExternalStorageState() in
                setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
    }

    private fun getFile(appContext: Context, fileName: String, internal: Boolean = false): File {
        return if(internal)
                File(appContext.filesDir, fileName)
            else {
                val file = File(ContextCompat.getExternalFilesDirs(appContext, null).first(), fileName)
                file
            }
    }
}


private fun getHttpClient(): OkHttpClient {
    return OkHttpClient.Builder().
        build()
}