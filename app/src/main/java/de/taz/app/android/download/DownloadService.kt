package de.taz.app.android.download

import android.content.Context
import androidx.work.*
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ACTION_DOWNLOAD = "action.download"
const val DATA_DOWNLOAD = "extra.download"
const val RECEIVER = "receiver"

const val RESOURCE_FOLDER = "resources"


object DownloadService {

    private val log by Log
    private val ioScope = CoroutineScope(Dispatchers.IO)

    fun download(appContext: Context, resourceInfo: ResourceInfo) {
        log.info("downloading resources")
        ioScope.launch {
            DownloadWorker.startDownloads(
                appContext,
                resourceInfo.resourceList.map {
                    Download(
                        resourceInfo.resourceBaseUrl,
                        RESOURCE_FOLDER,
                        it
                    )
                })
        }
    }

    fun download(appContext: Context, issue: Issue) {
        log.info("downloading issue(${issue.feedName}/${issue.date})")
        ioScope.launch {
            DownloadWorker.startDownloads(
                appContext,
                issue.fileList.filter { !it.startsWith("/global") }.map {
                    Download(
                        issue.baseUrl,
                        issue.date,
                        it
                    )
                })
        }
    }

    fun scheduleDownload(appContext: Context, resourceInfo: ResourceInfo) {
        enqueueDownloads(appContext, resourceInfo.resourceList.map { Download(resourceInfo.resourceBaseUrl, RESOURCE_FOLDER, it) })
    }

    fun scheduleDownload(appContext: Context, issue: Issue) {
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
        val data = Data.Builder()
            .putString(DATA_DOWNLOAD, download.serialize())
            .build()

        val constraints = Constraints.Builder() // TODO read constraints from settings
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true)
            .setRequiresBatteryNotLow(true)
            .build()

        return OneTimeWorkRequest.Builder(WorkManagerDownloadWorker::class.java)
            .setInputData(data)
            .setConstraints(constraints)
            .build()
    }
}
