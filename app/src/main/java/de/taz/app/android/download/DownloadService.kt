package de.taz.app.android.download

import android.content.Context
import androidx.work.*
import de.taz.app.android.PREFERENCES_DOWNLOADS
import de.taz.app.android.R
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.SETTINGS_DOWNLOAD_ONLY_WIFI
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.*
import java.util.*

const val DATA_ISSUE_DATE = "extra.issue.date"
const val DATA_ISSUE_FEEDNAME = "extra.issue.feedname"
const val DATA_ISSUE_STATUS = "extra.issue.status"


const val CAUSE_NO_INTERNET = "no internet"

@Mockable
class DownloadService private constructor(val applicationContext: Context) {

    companion object : SingletonHolder<DownloadService, Context>(::DownloadService)

    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val apiService = ApiService.getInstance(applicationContext)
    private val appInfoRepository = AppInfoRepository.getInstance(applicationContext)
    private val downloadRepository = DownloadRepository.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val issueRepository = IssueRepository.getInstance(applicationContext)

    private val downloadJobs = Collections.synchronizedList(mutableListOf<Job>())

    /**
     * use [ioScope] to download
     * @param cacheableDownload - object implementing the [CacheableDownload] interface
     *                            it's files will be downloaded
     */
    suspend fun download(cacheableDownload: CacheableDownload, baseUrl: String? = null) {

        if (appInfoRepository.get() == null) {
            AppInfo.update()
        }

        var downloadId: String? = null
        val start: Long = System.currentTimeMillis()
        val issue = if (cacheableDownload is Issue) cacheableDownload else null

        if (!cacheableDownload.isDownloadedOrDownloading()
            && appInfoRepository.get() != null
        ) {
            issue?.let {
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

                createDownloadsForCacheableDownload(cacheableDownload, baseUrl)

                DownloadWorker(applicationContext).startDownloads(
                    cacheableDownload.getAllFiles()
                ).joinAll()
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
                        ToastHelper.getInstance(applicationContext)
                            .showToast(R.string.toast_no_internet)
                    }
                }
            }
        }
    }

    /**
     * download issue in background
     */
    fun scheduleIssueDownload(issueOperations: IssueOperations): Operation {

        val data = Data.Builder()
            .putString(DATA_ISSUE_STATUS, issueOperations.status.toString())
            .putString(DATA_ISSUE_DATE, issueOperations.date)
            .putString(DATA_ISSUE_FEEDNAME, issueOperations.feedName)
            .build()

        val requestBuilder = OneTimeWorkRequest.Builder(IssueDownloadWorkManagerWorker::class.java)
            .setInputData(data)
            .setConstraints(getConstraints())
            .addTag(issueOperations.tag)

        return WorkManager.getInstance(applicationContext).enqueue(requestBuilder.build())
    }

    /**
     * cancel all running download jobs
     */
    fun cancelAllDownloads() {
        downloadJobs.forEach {
            it.cancel()
        }
    }

    private fun createDownloadsForCacheableDownload(
        cacheableDownload: CacheableDownload,
        baseUrl: String? = null
    ): List<Download> {
        val downloads = mutableListOf<Download>()

        val allFiles = fileEntryRepository.get(cacheableDownload.getAllFileNames())
        val tag = cacheableDownload.getDownloadTag()


        // create single FileEntry downloads if baseUrl is given
        if (baseUrl != null &&
            cacheableDownload is FileEntry
        ) {
            downloads.addAll(
                createAndSaveDownloads(
                    baseUrl,
                    allFiles,
                    tag
                )
            )
        } else {
            val globalFiles = allFiles.filter { it.storageType == StorageType.global }
            if (globalFiles.isNotEmpty()) {
                appInfoRepository.get()?.let {
                    downloads.addAll(
                        createAndSaveDownloads(
                            it.globalBaseUrl,
                            globalFiles,
                            tag
                        )
                    )
                }
            }

            // create resource downloads
            val resourceFiles = allFiles.filter { it.storageType == StorageType.resource }
            if (resourceFiles.isNotEmpty()) {
                val resourceInfo = ResourceInfoRepository.getInstance(applicationContext).get()
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

        }

        return downloads
    }


    /**
     * get Constraints for [WorkRequest] of [WorkManager]
     */
    private fun getConstraints(): Constraints {
        val onlyWifi: Boolean =
            applicationContext.getSharedPreferences(PREFERENCES_DOWNLOADS, Context.MODE_PRIVATE)
                ?.let {
                    SharedPreferenceBooleanLiveData(it, SETTINGS_DOWNLOAD_ONLY_WIFI, true).value
                } ?: true

        return Constraints.Builder()
            .setRequiredNetworkType(if (onlyWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()
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
