package de.taz.app.android.download

import android.content.Context
import androidx.work.*
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.models.Download
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Issue
import de.taz.app.android.persistence.repository.AppInfoRepository
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.util.ToastHelper
import kotlinx.coroutines.*
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.*
import java.util.concurrent.TimeUnit

const val DATA_DOWNLOAD_FILE_NAME = "extra.download.file.name"
const val DATA_ISSUE_FEEDNAME = "extra.issue.feedname"
const val DATA_ISSUE_DATE = "extra.issue.date"


const val CAUSE_NO_INTERNET = "no internet"

object DownloadService {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val apiService = ApiService.getInstance()
    private val appInfoRepository = AppInfoRepository.getInstance()
    private val downloadRepository = DownloadRepository.getInstance()
    private val issueRepository = IssueRepository.getInstance()

    private val okHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
        .build()

    private val downloadJobs = Collections.synchronizedList(mutableListOf<Job>())

    /**
     * use [ioScope] to download
     * @param cacheableDownload - object implementing the [CacheableDownload] interface
     *                            it's files will be downloaded
     */
    fun download(appContext: Context, cacheableDownload: CacheableDownload) {

        if (!cacheableDownload.isDownloadedOrDownloading()) {

            var downloadId: String? = null
            val start: Long = System.currentTimeMillis()
            val issue = if (cacheableDownload is Issue) cacheableDownload else null

            issue?.let{
                issueRepository.setDownloadDate(issue, Date())
            }

            val job = ioScope.launch {
                try {
                    downloadId = issue?.let {
                        apiService.notifyServerOfDownloadStart(issue.feedName, issue.date)
                    }
                } catch (e: ApiService.ApiServiceException.NoInternetException) {
                    this.cancel(CAUSE_NO_INTERNET, e)
                }

                createDownloadsForCacheableDownload(appContext, cacheableDownload)

                DownloadWorker(okHttpClient).startDownloads(
                    appContext,
                    cacheableDownload.getAllFiles()
                )
            }

            downloadJobs.add(job)

            job.invokeOnCompletion { cause ->
                // remove the job
                downloadJobs.remove(job)

                // tell server we downloaded complete issue
                if (cause == null && issue != null) {
                    downloadId?.let { downloadId ->
                        val seconds = ((System.currentTimeMillis() - start) / 1000).toFloat()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                            apiService.notifyServerOfDownloadStop(
                                downloadId,
                                seconds
                            )
                            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                                // do not tell server we downloaded as we do not have internet
                            }
                        }
                    }
                } else {
                    if (cause is ApiService.ApiServiceException.NoInternetException) {
                        ToastHelper.getInstance(appContext).makeToast(R.string.toast_no_internet)
                    }
                }
            }
        }
    }

    /**
     * use [WorkManager] to download in background
     * @param cacheableDownload - object implementing [CacheableDownload] to download files of
     */
    fun scheduleDownload(appContext: Context, cacheableDownload: CacheableDownload) {
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
     * cancel all running download jobs
     */
    fun cancelAllDownloads() {
        downloadJobs.forEach {
            it.cancel()
        }
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

        val globalFiles = allFiles.filter { it.storageType == StorageType.global }
        if (globalFiles.isNotEmpty()) {
            downloads.addAll(
                createAndSaveDownloads(
                    appInfoRepository.getOrThrow().globalBaseUrl,
                    globalFiles,
                    tag
                )
            )
        }

        // create resource downloads
        val resourceFiles = allFiles.filter { it.storageType == StorageType.resource }
        if (resourceFiles.isNotEmpty()) {
            val resourceInfo = ResourceInfoRepository.getInstance(appContext).get()
            resourceInfo?.let {
                downloads.addAll(
                    createAndSaveDownloads(
                        resourceInfo.resourceBaseUrl,
                        resourceFiles,
                        tag
                    )
                )
            }
        }

        // create issue downloads
        val issueFiles = allFiles.filter { it.storageType == StorageType.issue }
        cacheableDownload.getIssueOperations()?.let { issue ->
            downloads.addAll(
                createAndSaveDownloads(
                    issue.baseUrl,
                    issueFiles,
                    tag
                )
            )
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
        fileEntries: List<FileEntry>,
        tag: String?
    ): List<Download> {
        return fileEntries.map { createAndSaveDownload(baseUrl, it, tag) }
    }

    /**
     * create [Download] for [FileEntry] and persist to DB
     * @param fileEntry - [FileEntry] to create Download of
     * @return created [Download]
     */
    private fun createAndSaveDownload(
        baseUrl: String,
        fileEntry: FileEntry,
        tag: String?
    ): Download {
        val download = Download(
            baseUrl,
            fileEntry,
            tag = tag
        )
        downloadRepository.saveIfNotExists(download)
        return download
    }
}
