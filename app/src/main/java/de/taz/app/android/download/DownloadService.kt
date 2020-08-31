package de.taz.app.android.download

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.work.*
import de.taz.app.android.PREFERENCES_DOWNLOADS
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.*
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.awaitCallback
import io.sentry.Sentry
import io.sentry.connection.ConnectionException
import kotlinx.coroutines.*
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

const val DATA_ISSUE_DATE = "extra.issue.date"
const val DATA_ISSUE_FEEDNAME = "extra.issue.feedname"
const val DATA_ISSUE_STATUS = "extra.issue.status"

const val CONCURRENT_DOWNLOAD_LIMIT = 10

@Mockable
class DownloadService private constructor(val applicationContext: Context) {

    companion object : SingletonHolder<DownloadService, Context>(::DownloadService)

    private val apiService = ApiService.getInstance(applicationContext)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val appInfoRepository = AppInfoRepository.getInstance(applicationContext)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val downloadRepository = DownloadRepository.getInstance(applicationContext)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val fileHelper = FileHelper.getInstance(applicationContext)
    val issueRepository = IssueRepository.getInstance(applicationContext)
    private val serverConnectionHelper = ServerConnectionHelper.getInstance(applicationContext)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)

    private var appInfo: AppInfo? = null
    private var resourceInfo: ResourceInfo? = null

    private val httpClient = OkHttp.client

    private val downloadList = ConcurrentLinkedDeque<Download>()

    private val currentDownloads = AtomicInteger(0)
    private val currentDownloadList = ConcurrentLinkedQueue<String>()

    private val tagJobMap = ConcurrentHashMap<String, MutableList<Job>>()

    init {
        Transformations.distinctUntilChanged(serverConnectionHelper.isDownloadServerReachableLiveData)
            .observeForever { isConnected ->
                if (isConnected) {
                    startDownloadsIfCapacity()
                }
            }
    }

    /**
     * start download for cacheableDownload
     * @param cacheableDownload - [CacheableDownload] to download
     * @param baseUrl - [String] providing the baseUrl - only necessary for downloads
     *                  where the baseUrl can not be automatically calculated (mostly [FileEntry])
     */
    fun download(cacheableDownload: CacheableDownload, baseUrl: String? = null): Job =
        CoroutineScope(Dispatchers.IO).launch {
            val start = DateHelper.now
            var redoJob: Job? = null
            if (!cacheableDownload.isDownloaded(applicationContext)) {
                ensureAppInfo()

                val issue = cacheableDownload as? Issue
                var downloadId: String? = null

                cacheableDownload.setDownloadStatus(DownloadStatus.started)

                // if we download an issue tell the server we start downloading it
                issue?.let {
                    downloadId =
                        apiService.notifyServerOfDownloadStartAsync(issue.feedName, issue.date)
                            .await()
                    issueRepository.setDownloadDate(it, Date())
                    redoJob = launch {
                        // check if metadata has changed and update db and restart download
                        val fromServer = apiService.getIssueByFeedAndDateAsync(
                            issue.feedName, issue.date
                        ).await()
                        if (
                            fromServer?.status == issue.status && fromServer.moTime != issue.moTime
                        ) {
                            cancelDownloadsForTag(issue.tag)
                            issueRepository.save(fromServer)
                            download(fromServer).join()
                        }
                    }
                }

                // wait for [CacheableDownload]'s files to be downloaded
                val isDownloadedLiveData = Transformations.distinctUntilChanged(
                    DownloadRepository.getInstance()
                        .isDownloadedLiveData(cacheableDownload.getAllFileNames())
                )
                val observer = object : Observer<Boolean> {
                    override fun onChanged(t: Boolean?) {
                        if (t == true) {
                            isDownloadedLiveData.removeObserver(this)
                            CoroutineScope(Dispatchers.IO).launch {
                                // mark download as downloaded - if it is an issue including articles etc
                                issue?.setDownloadStatusIncludingChildren(DownloadStatus.done)
                                    ?: run {
                                        cacheableDownload.setDownloadStatus(DownloadStatus.done)
                                    }
                                log.debug("download of ${cacheableDownload::class.java} complete in ${DateHelper.now - start}")
                                // notify server of completed download
                                downloadId?.let { downloadId ->
                                    val seconds: Float =
                                        (System.currentTimeMillis() - start) / 1000f
                                    apiService.notifyServerOfDownloadStopAsync(
                                        downloadId,
                                        seconds
                                    ).await()
                                }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) { isDownloadedLiveData.observeForever(observer) }

                // create Downloads
                addDownloadsToDownloadList(cacheableDownload, baseUrl)
                redoJob?.join()
            }
        }


    /**
     * start downloads if there are less then [CONCURRENT_DOWNLOAD_LIMIT] downloads started atm
     * and the server is reachable
     */
    private fun startDownloadsIfCapacity() {
        while (serverConnectionHelper.isDownloadServerReachable && currentDownloads.get() < CONCURRENT_DOWNLOAD_LIMIT) {
            downloadList.pollFirst()?.let { download ->
                if (!currentDownloadList.contains(download.fileName)) {
                    currentDownloadList.offer(download.fileName)
                    currentDownloads.incrementAndGet()
                    val job = CoroutineScope(Dispatchers.IO).launch {
                        getFromServer(download)
                        val start = DateHelper.now
                        log.info("download ${download.fileName} started")
                        log.info("download ${download.fileName} completed - ${DateHelper.now - start}")
                        startDownloadsIfCapacity()
                    }
                    download.tag?.let { tag ->
                        val jobsForTag = tagJobMap[tag] ?: mutableListOf()
                        jobsForTag.add(job)
                        tagJobMap[download.tag] = jobsForTag
                    }
                    job.invokeOnCompletion {
                        download.tag?.let { tagJobMap[it]?.remove(job) }
                    }
                }
            } ?: break
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun getFromServer(fileName: String) = runBlocking {
        downloadRepository.get(fileName)?.let {
            getFromServer(it, true)
        }
    }

    /**
     * call server to get response
     */
    private suspend fun getFromServer(
        download: Download,
        @VisibleForTesting(otherwise = VisibleForTesting.NONE) doNotRestartDownload: Boolean = false
    ) {
        try {
            val fileEntry = download.file
            downloadRepository.getStub(download.fileName)?.let { fromDB ->
                // download only if not already downloaded or downloading
                if (fromDB.lastSha256 != fileEntry.sha256 || fromDB.status !in arrayOf(
                        DownloadStatus.done,
                        DownloadStatus.started
                    )
                ) {
                    downloadRepository.setStatus(fromDB, DownloadStatus.started)

                    val response = awaitCallback(
                        httpClient.newCall(
                            Request.Builder().url(fromDB.url).get().build()
                        )::enqueue
                    )
                    handleResponse(response, download, doNotRestartDownload)
                } else {
                    log.debug("skipping download of ${fromDB.fileName} - already downloading/ed")
                }
            }
        } catch (e: Exception) {
            when (e) {
                is UnknownHostException,
                is ConnectionException,
                is ConnectException
                -> {
                    serverConnectionHelper.isDownloadServerReachable = false
                    abortAndRetryDownload(download)
                    DownloadService.log.warn("aborted download of ${download.fileName} - ${e.localizedMessage}")
                }
                is SSLException,
                is IOException,
                is SSLHandshakeException,
                is SocketTimeoutException -> {
                    abortAndRetryDownload(download)
                    DownloadService.log.warn("aborted download of ${download.fileName} - ${e.localizedMessage}")
                }
                is CancellationException -> {
                    DownloadService.log.info("download of ${download.fileName} has been canceled")
                    // cancellation was requested by program so do not retry
                    download.file.setDownloadStatus(DownloadStatus.pending)
                    downloadRepository.setStatus(download, DownloadStatus.pending)
                    currentDownloads.decrementAndGet()
                    currentDownloadList.remove(download.fileName)
                }
                else -> {
                    DownloadService.log.warn("unknown error occurred - ${download.fileName}")
                    abortAndRetryDownload(download)
                    Sentry.capture(e)
                    throw e
                }
            }
        }
    }

    /**
     * save server response to file, calculate sha and compare
     */
    private fun handleResponse(
        response: Response, download: Download,
        @VisibleForTesting(otherwise = VisibleForTesting.NONE) doNotRestartDownload: Boolean = false
    ) {
        val fileEntry = download.file
        if (response.isSuccessful) {
            response.body?.source()?.let { source ->
                // ensure folders are created
                fileHelper.createFileDirs(fileEntry)
                val sha256 = fileHelper.writeFile(fileEntry, source)
                downloadRepository.saveLastSha256(download, sha256)
                if (sha256 == fileEntry.sha256) {
                    downloadRepository.setStatus(download, DownloadStatus.done)
                    fileEntry.setDownloadStatus(DownloadStatus.done)
                } else {
                    log.warn("sha256 did NOT match the one of ${download.fileName}")
                    fileEntry.setDownloadStatus(DownloadStatus.takeOld)
                    downloadRepository.setStatus(download, DownloadStatus.takeOld)
                }
                currentDownloads.decrementAndGet()
                currentDownloadList.remove(download.fileName)
            } ?: run {
                log.debug("aborted download of ${download.fileName} - file is empty")
                abortAndRetryDownload(download, doNotRestartDownload)
            }
        } else {
            log.warn("Download was not successful ${response.code}")
            if (response.code in 400..599) {
                serverConnectionHelper.isDownloadServerReachable = false
            }
            abortAndRetryDownload(download, doNotRestartDownload)
        }
    }

    /**
     * save Download and FileEntry as not downloaded in database then retry download
     * @param doNotRetry indicating whether to retry download - only for testing
     */
    private fun abortAndRetryDownload(
        download: Download,
        @VisibleForTesting(otherwise = VisibleForTesting.NONE) doNotRetry: Boolean = false
    ) {
        download.file.setDownloadStatus(DownloadStatus.aborted)
        downloadRepository.setStatus(download, DownloadStatus.aborted)
        currentDownloads.decrementAndGet()
        currentDownloadList.remove(download.fileName)
        if (!doNotRetry) {
            prependToDownloadList(download)
        }
    }

    /**
     * download issue in background
     */
    fun scheduleIssueDownload(issueOperations: IssueOperations): Operation? {
        return scheduleIssueDownload(
            issueOperations.feedName,
            issueOperations.date,
            issueOperations.status,
            issueOperations.tag
        )
    }

    /**
     * download issue in background
     */
    fun scheduleIssueDownload(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus,
        tag: String
    ): Operation? {
        val data = Data.Builder()
            .putString(DATA_ISSUE_STATUS, issueStatus.toString())
            .putString(DATA_ISSUE_DATE, issueDate)
            .putString(DATA_ISSUE_FEEDNAME, issueFeedName)
            .build()

        val requestBuilder =
            OneTimeWorkRequest.Builder(IssueDownloadWorkManagerWorker::class.java)
                .setInputData(data)
                .setConstraints(getConstraints())
                .addTag(tag)

        return WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            tag,
            ExistingWorkPolicy.KEEP,
            requestBuilder.build()
        )
    }

    /**
     * cancel all running download jobs
     */
    fun cancelDownloadsForTag(tag: String) {
        downloadList.removeAll(downloadList.filter { it.tag == tag })
        val jobsForTag = tagJobMap.remove(tag)
        jobsForTag?.forEach { it.cancel() }
    }

    /**
     * cancel all issue downloads
     */
    fun cancelIssueDownloads() {
        IssueRepository.getInstance(applicationContext).getDownloadStartedIssueStubs().forEach {
            cancelDownloadsForTag(it.tag)
        }
    }

    /**
     * create downloads, add them to the downloadList and start downloading
     * @param cacheableDownload [CacheableDownload] to create Downloads from
     * @param baseUrl - [String] providing the baseUrl - only necessary for downloads
     *                  where the baseUrl can not be automatically calculated (mostly [FileEntry])
     */
    private suspend fun addDownloadsToDownloadList(
        cacheableDownload: CacheableDownload,
        baseUrl: String? = null
    ) = withContext(Dispatchers.IO) {

        val tag = cacheableDownload.getDownloadTag()
        val issueOperations = cacheableDownload.getIssueOperations(applicationContext)

        // create Downloads and save them in the database
        cacheableDownload.getAllFileNames().forEach {
            fileEntryRepository.get(it)?.let { fileEntry ->
                if (fileEntry.downloadedStatus != DownloadStatus.done) {
                    val download: Download? =
                        if (baseUrl != null && cacheableDownload is FileEntry) {
                            createAndSaveDownload(baseUrl, fileEntry, tag)
                        } else {
                            when (fileEntry.storageType) {
                                StorageType.global -> {
                                    ensureAppInfo()
                                    appInfo?.globalBaseUrl?.let { globalBaseUrl ->
                                        createAndSaveDownload(globalBaseUrl, fileEntry, tag)
                                    }
                                }
                                StorageType.resource -> {
                                    ensureResourceInfo()
                                    resourceInfo?.resourceBaseUrl?.let { resourceBaseUrl ->
                                        createAndSaveDownload(resourceBaseUrl, fileEntry, tag)
                                    }
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
                        if (!currentDownloadList.contains(download.fileName)
                            && !download.file.isDownloaded(applicationContext)
                        ) {
                            // issues are not shown immediately - so download other downloads like articles first
                            if (cacheableDownload is Issue) {
                                appendToDownloadList(download)
                            } else {
                                prependToDownloadList(download)
                            }
                        }
                    } ?: log.debug("creating download for ${fileEntry.name} returned null")
                }
            }
        }
    }

    private suspend fun ensureAppInfo() {
        if (appInfo == null) {
            AppInfo.get(applicationContext)
            appInfo = appInfoRepository.get()
        }
    }

    private fun ensureResourceInfo() {
        if (resourceInfo == null) {
            resourceInfo = ResourceInfo.get(applicationContext)
        }
    }

    private fun appendToDownloadList(download: Download) {
        if (!currentDownloadList.contains(download.fileName) && !downloadList.contains(download)) {
            if (downloadList.offerLast(download)) {
                startDownloadsIfCapacity()
            }
        }
    }

    private fun prependToDownloadList(download: Download) {
        if (!currentDownloadList.contains(download.fileName)) {
            downloadList.removeAll(downloadList.filter { it.fileName == download.fileName })
            if (downloadList.offerFirst(download)) {
                startDownloadsIfCapacity()
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
        val fromDB = downloadRepository.get(fileEntry.name)
        return if (fromDB == null) {
            val download = Download(
                baseUrl,
                fileEntry,
                tag = tag
            )
            if (downloadRepository.saveIfNotExists(download)) download else null
        } else {
            fromDB
        }
    }
}
