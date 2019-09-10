package de.taz.app.android.download

import android.content.Context
import androidx.work.*
import de.taz.app.android.api.models.Download
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val DATA_DOWNLOAD_FILE_NAME = "extra.download.file.name"

const val RESOURCE_FOLDER = "resources"
const val RESOURCE_TAG = "resources"


object DownloadService {

    private val log by Log
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val fileEntryRepository = FileEntryRepository()
    private val downloadRepository = DownloadRepository()

    /**
     * use [ioScope] to download
     * @param resourceInfo - [ResourceInfo] to download files of
     */
    fun download(appContext: Context, resourceInfo: ResourceInfo) {
        log.info("downloading resources")
        createAndSaveDownloads(
            resourceInfo.resourceBaseUrl,
            RESOURCE_FOLDER,
            resourceInfo.resourceList
        )
        ioScope.launch {
            DownloadWorker.startDownloads(
                appContext,
                resourceInfo.resourceList
            )
        }
    }

    /**
     * use [ioScope] to download
     * @param issue - Issue to download files of
     */
    fun download(appContext: Context, issue: Issue) {
        log.info("downloading issue(${issue.feedName}/${issue.date})")
        ioScope.launch {
            issue.fileList.mapNotNull { fileEntryRepository.get(it.split("/").last()) }
                .let { files ->
                    createAndSaveDownloads(issue.baseUrl, issue.tag, files)

                    DownloadWorker.startDownloads(
                        appContext,
                        files
                    )
                }
        }
    }

    /**
     * use [WorkManager] to download in background
     * @param resourceInfo - [ResourceInfo] to download files of
     */
    fun scheduleDownload(appContext: Context, resourceInfo: ResourceInfo) {
        enqueueDownloads(
            appContext,
            createAndSaveDownloads(
                resourceInfo.resourceBaseUrl,
                RESOURCE_FOLDER,
                resourceInfo.resourceList
            ),
            RESOURCE_TAG
        )
    }

    /**
     * use [WorkManager] to download in background
     * @param issue - Issue to download files of
     */
    fun scheduleDownload(appContext: Context, issue: Issue) {
        val downloads = createAndSaveDownloads(
            issue.baseUrl,
            issue.tag,
            issue.fileList.mapNotNull {
                fileEntryRepository.get(it.split("/").last())
            }
        )
        enqueueDownloads(appContext, downloads, issue.tag)
    }

    /**
     * enqueue [Download]s with [WorkManager]
     * @param appContext - [Context] of the app
     * @param downloads - downloads to enqueue
     * @param tag - tag for the downloads (can be used to cancel downloads)
     */
    private fun enqueueDownloads(
        appContext: Context,
        downloads: List<Download>,
        tag: String? = null
    ): Operation {
        val requests = downloads.map { createRequestAndUpdate(it, tag) }
        return WorkManager.getInstance(appContext).enqueue(requests)
    }

    /**
     * create [OneTimeWorkRequest] for [WorkManager] from [Download]
     * @param download - Download to create Request from
     * @param tag - tag for Download (can be used to cancel downloads)
     * @return [OneTimeWorkRequest]
     */
    private fun createRequest(download: Download, tag: String? = null): OneTimeWorkRequest {
        val data = Data.Builder()
            .putString(DATA_DOWNLOAD_FILE_NAME, download.file.name)
            .build()

        val constraints = Constraints.Builder() // TODO read constraints from settings
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val requestBuilder = OneTimeWorkRequest.Builder(WorkManagerDownloadWorker::class.java)
            .setInputData(data)
            .setConstraints(constraints)

        tag?.let { requestBuilder.addTag(tag) }

        return requestBuilder.build()
    }

    /**
     * create [OneTimeWorkRequest] for [WorkManager] from  [Download] and save requestId
     * @param download - Download to create request from and update with requestId
     * @param tag - tag for Download (can be used to cancel downloads)
     * @return [OneTimeWorkRequest]
     */
    private fun createRequestAndUpdate(
        download: Download,
        tag: String? = null
    ): OneTimeWorkRequest {
        val request = createRequest(download, tag)
        downloadRepository.setWorkerId(download.file.name, request.id)
        return request
    }

    /**
     * create [Download]s for [FileEntry]s and persist them in DB
     * @param fileEntries - [FileEntry]s to create [Download]s from
     * @return [List] of created [Download]s
     */
    private fun createAndSaveDownloads(
        baseUrl: String,
        folderPath: String,
        fileEntries: List<FileEntry>
    ): List<Download> {
        return fileEntries.map { createAndSaveDownload(baseUrl, folderPath, it) }
    }

    /**
     * create [Download] for [FileEntry] and persist to DB
     * @param fileEntry - [FileEntry] to create Download of
     * @return created [Download]
     */
    private fun createAndSaveDownload(
        baseUrl: String,
        folderPath: String,
        fileEntry: FileEntry
    ): Download {
        val download = Download(
            baseUrl,
            folderPath,
            fileEntry
        )
        downloadRepository.saveIfNotExists(download)
        return download
    }
}
