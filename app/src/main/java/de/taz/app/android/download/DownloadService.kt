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
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.singletons.ServerConnectionHelper
import de.taz.app.android.singletons.OkHttp
import de.taz.app.android.singletons.SETTINGS_DOWNLOAD_ONLY_WIFI
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

const val CONCURRENT_DOWNLOAD_LIMIT = 50

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
    private val workManager = WorkManager.getInstance(applicationContext)

    private val downloadList = ConcurrentLinkedDeque<Download>()

    private val currentDownloads = AtomicInteger(0)
    private val currentDownloadList = ConcurrentLinkedQueue<String>()

    private val tagJobMap = ConcurrentHashMap<String, MutableList<Job>>()

    init {
        Transformations.distinctUntilChanged(serverConnectionHelper.isServerReachableLiveData)
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
            if (!cacheableDownload.isDownloaded(applicationContext)) {
                ensureAppInfo()

                val issue = cacheableDownload as? Issue
                var downloadId: String? = null
                val start = System.currentTimeMillis()

                // if we download an issue tell the server we start downloading it
                issue?.let {
                    downloadId =
                        apiService.notifyServerOfDownloadStartAsync(issue.feedName, issue.date)
                            .await()
                    issueRepository.setDownloadDate(it, Date())
                    launch {
                        // check if metadata has changed and update db and restart download
                        val fromServer = apiService.getIssueByFeedAndDateAsync(
                            issue.feedName, issue.date
                        ).await()
                        if (
                            fromServer?.status == issue.status && fromServer.moTime != issue.moTime
                        ) {
                            cancelDownloads(issue.tag)
                            issueRepository.save(fromServer)
                            download(fromServer)
                        }
                    }
                }

                cacheableDownload.setDownloadStatus(DownloadStatus.started)

                // wait for [CacheableDownload]'s files to be downloaded
                val isDownloadedLiveData = Transformations.distinctUntilChanged(
                    DownloadRepository.getInstance()
                        .isDownloadedLiveData(cacheableDownload.getAllFileNames())
                )
                val observer = object : Observer<Boolean> {
                    override fun onChanged(t: Boolean?) {
                        if (t == true) {
                            CoroutineScope(Dispatchers.IO).launch {
                                // mark download as downloaded - if it is an issue including articles etc
                                issue?.setDownloadStatusIncludingChildren(DownloadStatus.done)
                                    ?: run {
                                        cacheableDownload.setDownloadStatus(DownloadStatus.done)
                                    }
                            }
                            isDownloadedLiveData.removeObserver(this)

                            // notify server of completed download
                            downloadId?.let { downloadId ->
                                val seconds =
                                    ((System.currentTimeMillis() - start) / 1000).toFloat()
                                CoroutineScope(Dispatchers.IO).launch {
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
            }
        }


    /**
     * start downloads if there are less then [CONCURRENT_DOWNLOAD_LIMIT] downloads started atm
     * and the server is reachable
     */
    private fun startDownloadsIfCapacity() {
        CoroutineScope(Dispatchers.IO).launch {
            while (serverConnectionHelper.isServerReachable && currentDownloads.get() < CONCURRENT_DOWNLOAD_LIMIT) {
                downloadList.pollFirst()?.let { download ->
                    if (!currentDownloadList.contains(download.fileName)) {
                        currentDownloadList.offer(download.fileName)
                        currentDownloads.incrementAndGet()
                        val job = getFromServer(download)
                        download.tag?.let { tag ->
                            val jobsForTag = tagJobMap[tag] ?: mutableListOf()
                            jobsForTag.add(job)
                            tagJobMap[download.tag] = jobsForTag
                        }
                        job.invokeOnCompletion {
                            log.info("download ${download.fileName} completed")
                            currentDownloads.decrementAndGet()
                            currentDownloadList.remove(download.fileName)
                            startDownloadsIfCapacity()
                            download.tag?.let { tagJobMap[it]?.remove(job) }
                        }
                    }
                } ?: break
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun getFromServer(fileName: String) = runBlocking {
        downloadRepository.get(fileName)?.let {
            getFromServer(it, true).join()
        }
    }

    /**
     * call server to get response
     */
    private fun getFromServer(
        download: Download,
        @VisibleForTesting(otherwise = VisibleForTesting.NONE) doNotRestartDownload: Boolean = false
    ): Job =
        CoroutineScope(Dispatchers.IO).launch {
            val fileEntry = download.file
            downloadRepository.getStub(download.fileName)?.let { fromDB ->
                // download only if not already downloaded or downloading
                if (fromDB.lastSha256 != fileEntry.sha256 || fromDB.status !in arrayOf(
                        DownloadStatus.done,
                        DownloadStatus.started
                    )
                ) {
                    downloadRepository.setStatus(fromDB, DownloadStatus.started)

                    try {
                        val response = awaitCallback(
                            httpClient.newCall(
                                Request.Builder().url(fromDB.url).get().build()
                            )::enqueue
                        )
                        handleResponse(response, download, doNotRestartDownload)
                    } catch (e: Exception) {
                        downloadRepository.setStatus(fromDB, DownloadStatus.aborted)
                        currentDownloadList.remove(download.fileName)
                        when (e) {
                            is UnknownHostException,
                            is ConnectException
                            -> {
                                serverConnectionHelper.isServerReachable = false
                                appendToDownloadList(download)
                                log.warn("aborted download of ${fromDB.fileName} - ${e.localizedMessage}")
                            }
                            is IOException,
                            is SSLHandshakeException,
                            is SocketTimeoutException -> {
                                appendToDownloadList(download)
                                log.warn("aborted download of ${fromDB.fileName} - ${e.localizedMessage}")
                            }
                            is CancellationException -> {
                                log.info("download of ${fromDB.fileName} has been canceled")
                                throw e
                            }
                            else -> {
                                log.warn("unknown error occurred - ${fromDB.fileName}")
                                Sentry.capture(e)
                                throw e
                            }
                        }
                    }
                } else {
                    log.debug("skipping download of ${fromDB.fileName} - already downloading/ed")
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
        if (response.code.toString().startsWith("2")) {
            response.body?.source()?.let { source ->
                // ensure folders are created
                fileHelper.createFileDirs(fileEntry)
                try {
                    val sha256 = fileHelper.writeFile(fileEntry, source)
                    if (sha256 == fileEntry.sha256) {
                        downloadRepository.saveLastSha256(download, sha256)
                        downloadRepository.setStatus(download, DownloadStatus.done)
                        fileEntry.setDownloadStatus(DownloadStatus.done)
                    } else {
                        // TODO get new metadata for cacheableDownload and restart download
                        log.warn("sha256 did NOT match the one of ${download.fileName}")
                        if (fileHelper.getFile(fileEntry.name)?.exists() == true) {
                            fileEntry.setDownloadStatus(DownloadStatus.takeOld)
                            downloadRepository.setStatus(download, DownloadStatus.takeOld)
                        } else {
                            fileEntry.setDownloadStatus(DownloadStatus.aborted)
                            downloadRepository.setStatus(download, DownloadStatus.aborted)
                        }
                    }
                } catch (se: SSLException) {
                    log.debug("aborted download of ${download.fileName} - SSLException")
                    abortAndRetryDownload(download)
                } catch (ce: ConnectionException) {
                    log.debug("aborted download of ${download.fileName} - ConnectionException")
                    abortAndRetryDownload(download)
                } catch (ioe: IOException) {
                    log.debug("aborted download of ${download.fileName} - IOException")
                    abortAndRetryDownload(download)
                }
            } ?: run {
                log.debug("aborted download of ${download.fileName} - file is empty")
                abortAndRetryDownload(download, doNotRestartDownload)
            }
        } else {
            // TODO handle 50x by "backing off" and trying again later
            log.warn("Download was not successful ${response.code}")
            if (response.code in 400..499) {
                download.file.setDownloadStatus(DownloadStatus.aborted)
                downloadRepository.setStatus(download, DownloadStatus.aborted)
            } else {
                abortAndRetryDownload(download, doNotRestartDownload)
            }
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
    fun cancelDownloads(tag: String? = null) {
        tag?.let {
            downloadList.removeAll(downloadList.filter { it.tag == tag })
            val jobsForTag = tagJobMap.remove(tag)
            jobsForTag?.forEach { it.cancel() }
        } ?: downloadList.clear()
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

    private suspend fun ensureResourceInfo() {
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
