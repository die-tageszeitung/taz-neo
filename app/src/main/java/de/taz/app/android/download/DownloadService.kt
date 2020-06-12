package de.taz.app.android.download

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.*
import de.taz.app.android.PREFERENCES_DOWNLOADS
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.singletons.OkHttp
import de.taz.app.android.singletons.SETTINGS_DOWNLOAD_ONLY_WIFI
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.awaitCallback
import io.sentry.Sentry
import kotlinx.coroutines.*
import okhttp3.Request
import okhttp3.Response
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicInteger

const val DATA_ISSUE_DATE = "extra.issue.date"
const val DATA_ISSUE_FEEDNAME = "extra.issue.feedname"
const val DATA_ISSUE_STATUS = "extra.issue.status"

const val CAUSE_NO_INTERNET = "no internet"

const val CONCURRENT_DOWNLOAD_LIMIT = 10

@Mockable
class DownloadService private constructor(val applicationContext: Context) {

    companion object : SingletonHolder<DownloadService, Context>(::DownloadService)

    private val apiService = ApiService.getInstance(applicationContext)
    private val appInfoRepository = AppInfoRepository.getInstance(applicationContext)
    private val downloadRepository = DownloadRepository.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val fileHelper = FileHelper.getInstance(applicationContext)
    private val issueRepository = IssueRepository.getInstance(applicationContext)
    private val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)

    private val appInfo = appInfoRepository.get()
    private val resourceInfo = resourceInfoRepository.get()

    private val httpClient = OkHttp.getInstance(applicationContext).client
    private val workManager = WorkManager.getInstance(applicationContext)

    private val downloadList = LinkedBlockingDeque<Download>()
    private val currentDownloads = AtomicInteger(0)

    fun download(cacheableDownload: CacheableDownload, baseUrl: String? = null): Job =
        CoroutineScope(Dispatchers.IO).launch {
            if (!cacheableDownload.isDownloaded()) {
                // get AppInfo if not already there
                if (appInfoRepository.get() == null) {
                    AppInfo.update()
                }

                val issue = cacheableDownload as? Issue
                var downloadId: String? = null
                val start = System.currentTimeMillis()

                issue?.let {
                    downloadId = apiService.notifyServerOfDownloadStart(issue.feedName, issue.date)
                }

                val isDownloadedLiveData = cacheableDownload.isDownloadedLiveData()
                val observer = object : Observer<Boolean> {
                    override fun onChanged(t: Boolean?) {
                        if (t == true) {
                            isDownloadedLiveData.removeObserver(this)
                            log.debug("downloaded ${cacheableDownload.javaClass.name}")

                            issue?.let {
                                // set Issue download date
                                CoroutineScope(Dispatchers.IO).launch {
                                    issueRepository.setDownloadDate(it, Date())
                                }
                            }
                            downloadId?.let { downloadId ->
                                val seconds =
                                    ((System.currentTimeMillis() - start) / 1000).toFloat()
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
                        }
                    }
                }
                withContext(Dispatchers.Main) { isDownloadedLiveData.observeForever(observer) }

                log.debug("starting download of ${cacheableDownload.javaClass.name}")
                addDownloadsToDownloadList(cacheableDownload, baseUrl)
            }
        }


    private suspend fun startDownloads() {
        while (currentDownloads.get() < CONCURRENT_DOWNLOAD_LIMIT && downloadList.size > 0) {
            val downloadNames = downloadList.fold("downloadNames: ") { acc, elem ->
                acc + " ${elem.fileName}"
            }
            log.debug(downloadNames)

            downloadList.pollFirst()?.let { download ->
                currentDownloads.incrementAndGet()
                startDownload(download).invokeOnCompletion { _ ->
                    currentDownloads.decrementAndGet()
                    CoroutineScope(Dispatchers.IO).launch {
                        startDownloads()
                    }
                }
            }
        }
    }

    private suspend fun startDownload(download: Download): Job =
        CoroutineScope(Dispatchers.IO).launch {
            val fileEntry = download.file
            downloadRepository.getStub(download.fileName)?.let { fromDB ->
                // download only if not already downloaded or downloading
                if (fromDB.lastSha256 != fileEntry.sha256 || fromDB.status !in arrayOf(
                        DownloadStatus.done,
                        DownloadStatus.started,
                        DownloadStatus.takeOld
                    )
                ) {
                    log.debug("starting httpCall of ${fromDB.fileName}")

                    downloadRepository.setStatus(fromDB, DownloadStatus.started)

                    try {
                        val response = awaitCallback(
                            httpClient.newCall(
                                Request.Builder().url(fromDB.url).get().build()
                            )::enqueue
                        )
                        log.debug("finished http call of ${fromDB.fileName}")

                        // handle response in in anther job so we offer our httpConnection to next download
                        CoroutineScope(Dispatchers.IO).launch { handleResponse(response, download) }
                    } catch (e: Exception) {
                        log.warn("aborted download of ${fromDB.fileName} - ${e.localizedMessage}")
                        downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                        Sentry.capture(e)
                    }
                } else {
                    log.debug("skipping download of ${fromDB.fileName} - already downloading/ed")
                }
            }
        }

    private fun handleResponse(response: Response, download: Download) {
        log.debug("handling response for ${download.fileName}")
        val fileEntry = download.file
        if (response.code.toString().startsWith("2")) {
            response.body?.source()?.let { source ->
                // ensure folders are created
                fileHelper.createFileDirs(fileEntry)
                val sha256 = fileHelper.writeFile(fileEntry, source)
                if (sha256 == fileEntry.sha256) {
                    log.debug("sha256 matched for file ${download.fileName}")
                    download.workerManagerId?.let {
                        workManager.cancelWorkById(it)
                        log.info("canceling WorkerManagerRequest for ${download.fileName}")
                    }
                    val newDownload = download.copy(
                        lastSha256 = sha256,
                        status = DownloadStatus.done,
                        workerManagerId = null
                    )
                    downloadRepository.update(newDownload)
                    log.debug("finished download of ${download.fileName}")
                } else {
                    if (fileHelper.getFile(fileEntry.name)?.exists() == true) {
                        downloadRepository.setStatus(download, DownloadStatus.takeOld)
                    } else {
                        downloadRepository.setStatus(download, DownloadStatus.aborted)
                    }
                }
            } ?: run {
                log.debug("aborted download of ${download.fileName} - file is empty")
                downloadRepository.setStatus(download, DownloadStatus.aborted)
            }
        } else {
            log.warn("Download was not successful ${response.code}")
            downloadRepository.setStatus(download, DownloadStatus.aborted)
            Sentry.capture(response.message)
        }
        log.debug("finished handling response of ${download.fileName}")
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

        val requestBuilder =
            OneTimeWorkRequest.Builder(IssueDownloadWorkManagerWorker::class.java)
                .setInputData(data)
                .setConstraints(getConstraints())
                .addTag(issueOperations.tag)

        return WorkManager.getInstance(applicationContext).enqueue(requestBuilder.build())
    }

    /**
     * cancel all running download jobs
     */
    fun cancelAllDownloads() {
        downloadList.clear()
    }

    /**
     * create downloads, add them to the downloadList and start downloading
     * @param cacheableDownload to create Downloads from
     */
    private suspend fun addDownloadsToDownloadList(
        cacheableDownload: CacheableDownload,
        baseUrl: String? = null
    ) = withContext(Dispatchers.IO) {

        val tag = cacheableDownload.getDownloadTag()
        val issueOperations = cacheableDownload.getIssueOperations()

        cacheableDownload.getAllFileNames().forEach {
            fileEntryRepository.get(it)?.let { fileEntry ->
                val download: Download? = if (baseUrl != null && cacheableDownload is FileEntry) {
                    createAndSaveDownload(baseUrl, fileEntry, tag)
                } else {
                    when (fileEntry.storageType) {
                        StorageType.global ->
                            appInfo?.globalBaseUrl?.let { globalBaseUrl ->
                                createAndSaveDownload(globalBaseUrl, fileEntry, tag)
                            }
                        StorageType.resource ->
                            resourceInfo?.resourceBaseUrl?.let { resourceBaseUrl ->
                                createAndSaveDownload(resourceBaseUrl, fileEntry, tag)
                            }
                        StorageType.issue -> {
                            issueOperations?.baseUrl?.let { baseUrl ->
                                createAndSaveDownload(baseUrl, fileEntry, tag)
                            }
                        }
                        StorageType.public ->
                            // TODO?
                            null
                    }
                }
                download?.let {
                    if (cacheableDownload is Issue) {
                        downloadList.offerLast(download)
                    } else {
                        downloadList.offerFirst(download)
                    }
                    startDownloads()
                }
            }
        }
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
            .build()
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
    ): Download? {
        val download = Download(
            baseUrl,
            fileEntry,
            tag = tag
        )
        return if (downloadRepository.saveIfNotExists(download)) download else null
    }
}
