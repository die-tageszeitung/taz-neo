package de.taz.app.android.download

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.*
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.StorageType
import de.taz.app.android.api.models.Download
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Issue
import de.taz.app.android.persistence.repository.AppInfoRepository
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val DATA_DOWNLOAD_FILE_NAME = "extra.download.file.name"
const val DATA_ISSUE_FEEDNAME = "extra.issue.feedname"
const val DATA_ISSUE_DATE = "extra.issue.date"


const val GLOBAL_FOLDER = "global"
const val RESOURCE_FOLDER = "resources"


object DownloadService {

    private val log by Log
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val apiService = ApiService.getInstance()
    private val appInfoRepository = AppInfoRepository.getInstance()
    private val downloadRepository = DownloadRepository.getInstance()

    /**
     * use [ioScope] to download
     * @param cacheableDownload - object implementing the [CacheableDownload] interface
     *                            it's files will be downloaded
     */
    fun download(appContext: Context, cacheableDownload: CacheableDownload) {
        if (!cacheableDownload.isDownloadedOrDownloading()) {
            ioScope.launch {
                val issue = if (cacheableDownload is Issue) cacheableDownload else null

                val start = System.currentTimeMillis()
                val downloadId = issue?.let {
                    apiService.notifyServerOfDownloadStart(issue.feedName, issue.date)
                }

                createDownloadsForCacheableDownload(appContext, cacheableDownload)

                DownloadWorker.startDownloads(
                    appContext,
                    cacheableDownload.getAllFiles()
                )

                // if it's an issue tell the server we downloaded it
                issue?.let {
                    downloadId?.let {
                        val observer: Observer<Boolean> = object : Observer<Boolean> {
                            override fun onChanged(downloaded: Boolean) {
                                if (downloaded) {
                                    val observer = this
                                    issue.isDownloadedLiveData().removeObserver(observer)
                                    val seconds =
                                        ((System.currentTimeMillis() - start) / 1000).toFloat()
                                    ioScope.launch {
                                        apiService.notifyServerOfDownloadStop(
                                            downloadId,
                                            seconds
                                        )
                                    }
                                }
                            }
                        }

                        CoroutineScope(Dispatchers.Main).launch {
                            issue.isDownloadedLiveData().observeForever(observer)
                        }
                    }
                }
            }
        }
    }

    /**
     * use [WorkManager] to download in background
     * @param cacheableDownload - object implementing [CacheableDownload] to download files of
     */
    suspend fun scheduleDownload(appContext: Context, cacheableDownload: CacheableDownload) {
        enqueueDownloads(
            appContext,
            createDownloadsForCacheableDownload(appContext, cacheableDownload),
            cacheableDownload.getDownloadTag()
        )
    }

    /**
     * use [WorkManager] to get information about an issue and schedule downloads
     * @param issueFeedName: name of the feed of the issue
     * @param issueDate: date of the issue
     */
    fun scheduleDownload(appContext: Context, issueFeedName: String, issueDate: String) {
        WorkManager.getInstance(appContext)
            .enqueue(createScheduleDownloadsRequest(issueFeedName, issueDate))
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

    private fun createDownloadsForCacheableDownload(
        appContext: Context,
        cacheableDownload: CacheableDownload
    ): List<Download> {
        val downloads = mutableListOf<Download>()

        val allFiles = cacheableDownload.getAllFiles()
        val tag = cacheableDownload.getDownloadTag()

        // create global downloads
        val globalFiles = allFiles.filter { it.storageType == StorageType.global }
        if(globalFiles.isNotEmpty()) {
            downloads.addAll(createAndSaveDownloads(
                appInfoRepository.getOrThrow().globalBaseUrl,
                GLOBAL_FOLDER,
                globalFiles,
                tag
            ))
        }

        // create resource downloads
        val resourceFiles = allFiles.filter { it.storageType == StorageType.resource }
        if (resourceFiles.isNotEmpty()) {
            val resourceInfo = ResourceInfoRepository.getInstance(appContext).get()
            resourceInfo?.let {
                downloads.addAll(
                    createAndSaveDownloads(
                        resourceInfo.resourceBaseUrl,
                        RESOURCE_FOLDER,
                        resourceFiles,
                        tag
                    )
                )
            }
        }

        // create issue downloads
        val issueFiles = allFiles.filter { it.storageType == StorageType.issue }
        cacheableDownload.getIssueOperations()?.let { issue ->
            downloads.addAll(createAndSaveDownloads(
                issue.baseUrl,
                issue.tag,
                issueFiles,
                tag
            ))
        }

        return downloads
    }


    /**
     * create [OneTimeWorkRequest] for [WorkManager] from [Download]
     * @param download - Download to create Request from
     * @param tag - tag for Download (can be used to cancel downloads)
     * @return [OneTimeWorkRequest]
     */
    private fun createDownloadRequest(download: Download, tag: String? = null): OneTimeWorkRequest {
        val data = Data.Builder()
            .putString(DATA_DOWNLOAD_FILE_NAME, download.file.name)
            .build()

        val requestBuilder = OneTimeWorkRequest.Builder(WorkManagerDownloadWorker::class.java)
            .setInputData(data)
            .setConstraints(getConstraints())

        tag?.let { requestBuilder.addTag(tag) }

        return requestBuilder.build()
    }

    private fun createScheduleDownloadsRequest(feedName: String, date: String): OneTimeWorkRequest {
        val data =
            Data.Builder().putString(DATA_ISSUE_FEEDNAME, feedName).putString(DATA_ISSUE_DATE, date)
                .build()

        return OneTimeWorkRequest.Builder(ScheduleIssueDownloadWorkManagerWorker::class.java)
            .setInputData(data)
            .setConstraints(getConstraints())
            .addTag("$feedName/$date")
            .build()
    }

    /**
     * get Constraints for [WorkRequest] of [WorkManager]
     */
    private fun getConstraints(): Constraints {
        return Constraints.Builder() // TODO read constraints from settings
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true)
            .setRequiresBatteryNotLow(true)
            .build()
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
        val request = createDownloadRequest(download, tag)
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
        fileEntries: List<FileEntry>,
        tag: String?
    ): List<Download> {
        return fileEntries.map { createAndSaveDownload(baseUrl, folderPath, it, tag) }
    }

    /**
     * create [Download] for [FileEntry] and persist to DB
     * @param fileEntry - [FileEntry] to create Download of
     * @return created [Download]
     */
    private fun createAndSaveDownload(
        baseUrl: String,
        folderPath: String,
        fileEntry: FileEntry,
        tag: String?
    ): Download {
        val download = Download(
            baseUrl,
            folderPath,
            fileEntry,
            tag = tag
        )
        downloadRepository.saveIfNotExists(download)
        return download
    }
}
