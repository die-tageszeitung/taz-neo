package de.taz.app.android.download

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.work.*
import de.taz.app.android.CONCURRENT_FILE_DOWNLOADS
import de.taz.app.android.PREFERENCES_DOWNLOADS
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.*
import de.taz.app.android.api.transformToConnectivityException
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.*
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.awaitCallback
import io.sentry.core.Sentry
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.Request
import okhttp3.Response
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque


@Mockable
class DownloadService private constructor(val applicationContext: Context) {

    companion object : SingletonHolder<DownloadService, Context>(::DownloadService)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val apiService = ApiService.getInstance(applicationContext)
    private val fileHelper = FileHelper.getInstance(applicationContext)
    private val httpClient = OkHttp.client
    private val downloadQueue = ConcurrentLinkedDeque<TaggedDownloadJob>()
    private var downloaderJob: Job? = null

    private var maximumDownloadCounter = CounterLock(CONCURRENT_FILE_DOWNLOADS)

    private lateinit var downloadConnectionHelper: DownloadConnectionHelper

    /**
     * start download for cacheableDownload
     * @param downloadableCollection - [DownloadableCollection] to download
     * @param baseUrl - [String] providing the baseUrl - only necessary for downloads
     *                  where the baseUrl can not be automatically calculated (mostly [FileEntry])
     */
    suspend fun ensureCollectionDownloaded(
        downloadableCollection: DownloadableCollection,
        statusLiveData: MutableLiveData<DownloadStatus>,
        ensureIntegrity: Boolean = false,
        isAutomaticDownload: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        ensureDownloadHelper()
        if (downloadableCollection.isDownloaded()) {
            val isComplete = if (ensureIntegrity) {
                fileHelper.ensureFileListIntegrity(downloadableCollection.getAllFiles())
            } else {
                fileHelper.ensureFileListExists(downloadableCollection.getAllFiles())
            }
            if (isComplete) {
                statusLiveData.postValue(DownloadStatus.done)
                return@withContext
            }
        }
        statusLiveData.postValue(DownloadStatus.started)
        scheduleDownloadOrListenToRunning(
            downloadableCollection,
            isAutomaticDownload = isAutomaticDownload
        )
        statusLiveData.postValue(DownloadStatus.done)
        downloadableCollection.setDownloadDate(Date())

    }

    private suspend fun notifyIssueDownloadStart(
        issue: Issue,
        isAutomaticDownload: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            apiService.notifyServerOfDownloadStart(
                issue.feedName,
                issue.date,
                isAutomaticDownload
            )
        } catch (nie: ConnectivityException) {
            null
        }
    }

    private suspend fun notifyIssueDownloadStop(downloadId: String, timeTaken: Float) =
        withContext(Dispatchers.IO) {
            apiService.notifyServerOfDownloadStop(
                downloadId, timeTaken
            )
        }

    private suspend fun determineBaseUrl(
        collection: DownloadableCollection,
        fileEntry: FileEntry
    ): String = withContext(Dispatchers.IO) {
        val dataService = DataService.getInstance()
        when (fileEntry.storageType) {
            StorageType.global -> {
                dataService.getAppInfo().globalBaseUrl
            }
            StorageType.resource -> {
                dataService.getResourceInfo().resourceBaseUrl
            }
            StorageType.issue -> {
                when (collection) {
                    is Moment -> IssueRepository.getInstance().get(
                        IssueKey(
                            collection.issueFeedName,
                            collection.issueDate,
                            collection.issueStatus
                        )
                    )?.baseUrl
                        ?: throw CannotDetermineBaseUrlException("Unable to find issue for moment $collection")
                    is IssueOperations -> collection.baseUrl
                    is WebViewDisplayable -> collection.getIssueStub()?.baseUrl
                        ?: throw CannotDetermineBaseUrlException("${collection.key} has no issue")
                    else -> throw CannotDetermineBaseUrlException("$collection is not an issue but tried to download a file with storage type issue: ${fileEntry.name}")
                }
            }
            StorageType.public -> ""
        }

    }

    suspend fun downloadAndSaveFile(
        fileToDownload: FileEntry,
        baseUrl: String
    ) {
        ensureDownloadHelper()
        downloadConnectionHelper.retryOnConnectivityFailure {
            transformToConnectivityException {
                val response = awaitCallback(
                    httpClient.newCall(
                        Request.Builder().url("$baseUrl/${fileToDownload.name}").get().build()
                    )::enqueue
                )
                handleResponse(response, fileToDownload)
            }
        }
        fileEntryRepository.setDownloadDate(fileToDownload, Date())
    }

    /**
     * save server response to file, calculate sha and compare
     */
    private fun handleResponse(
        response: Response, downloadedFile: FileEntry
    ) {
        if (response.isSuccessful) {
            response.body?.let { body ->
                // ensure folders are created
                fileHelper.createFileDirs(downloadedFile)
                val sha256 = fileHelper.writeFile(downloadedFile, body.source())
                log.debug("${downloadedFile.name} saved")
                if (sha256 == downloadedFile.sha256) {
                    fileEntryRepository.setDownloadDate(downloadedFile, Date())
                } else {
                    fileEntryRepository.resetDownloadDate(
                        downloadedFile
                    )
                }
                body.close()
            } ?: run {
                log.debug("aborted download of ${downloadedFile.name} - file is empty")
            }
        } else {
            log.warn("Download of ${downloadedFile.name} not successful ${response.code}")
            Sentry.captureMessage(response.message)
            if (response.code in 400..499) {
                fileEntryRepository.resetDownloadDate(downloadedFile)
            } else if (response.code in 500..599) {
                throw ConnectivityException.ServerUnavailableException("Response code ${response.code} while trying to download ${downloadedFile.name}")
            }
        }
    }

    /**
     * download new issue in background
     */
    fun scheduleNewestIssueDownload(tag: String): Operation? {
        val requestBuilder =
            OneTimeWorkRequest.Builder(IssueDownloadWorkManagerWorker::class.java)
                .setConstraints(getConstraints())
                .addTag(tag)

        return WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            tag,
            ExistingWorkPolicy.KEEP,
            requestBuilder.build()
        )
    }

    /**
     * Cancel all scheduled or running downloads in queue
     * @param tag tag of download to cancel
     */
    suspend fun cancelDownloadForTag(tag: String) {
        val job = downloadQueue.find { it.tag == tag }
        job?.let {
            downloadQueue.remove(it)
            it.downloadJob.cancelAndJoin()
        }
    }

    /**
     * create downloads, add them to the downloadList and start downloading
     * @param downloadableCollection [DownloadableCollection] to create Downloads from
     */
    private suspend fun scheduleDownloadOrListenToRunning(
        downloadableCollection: DownloadableCollection,
        isAutomaticDownload: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val tag = downloadableCollection.getDownloadTag()
        val downloadJob = downloadQueue.find { it.tag == tag }?.let {
            it.downloadJob
        } ?: run {
            val newDownload = scheduleDownload(downloadableCollection, isAutomaticDownload)
            newDownload
        }

        downloadJob.join()
    }


    private suspend fun scheduleDownload(
        downloadableCollection: DownloadableCollection,
        isAutomaticDownload: Boolean = false
    ): Job =
        withContext(Dispatchers.IO) {
            val job = launch(start = CoroutineStart.LAZY) {
                val (start, downloadId) = if (downloadableCollection is Issue) {
                    val start = Date().time
                    val downloadId =
                        notifyIssueDownloadStart(downloadableCollection, isAutomaticDownload)
                    start to downloadId
                } else {
                    null to null
                }
                val filesToDownload =
                    fileEntryRepository.getList(downloadableCollection.getAllFileNames())
                filesToDownload
                    .map {
                        launch {
                            maximumDownloadCounter.withLock {
                                log.debug("Downloading ${it.name} for ${downloadableCollection.getDownloadTag()}")
                                downloadAndSaveFile(
                                    it,
                                    determineBaseUrl(downloadableCollection, it)
                                )
                            }
                        }
                    }
                    .map { it.join() }

                if (downloadableCollection is Issue) {
                    val secondsTaken = (Date().time - start!!).toFloat() / 1000
                    log.debug("It took $secondsTaken seconds for ${downloadableCollection.issueKey} to download all its assets (DownloadId: $downloadId")
                    downloadId?.let { notifyIssueDownloadStop(it, secondsTaken) }
                }
            }
            val taggedDownload = TaggedDownloadJob(downloadableCollection.getDownloadTag(), job)
            if (downloadableCollection is Issue) {
                downloadQueue.offerLast(taggedDownload)
            } else {
                downloadQueue.offerFirst(taggedDownload)
            }
            ensureDownloaderRunning()
            return@withContext job
        }

    private suspend fun ensureDownloadHelper() {
        if (!::downloadConnectionHelper.isInitialized) {
            downloadConnectionHelper = DownloadConnectionHelper(
                DataService.getInstance().getAppInfo().globalBaseUrl,
                httpClient
            )
            log.debug("Initialized downloadConnectionHelper")
        }
    }

    private fun ensureDownloaderRunning() {
        if (downloaderJob?.isCompleted == false) {
            return
        } else {
            downloaderJob = CoroutineScope(Dispatchers.IO).launch {
                while (downloadQueue.isNotEmpty()) {
                    val nextJob = downloadQueue.peekFirst()
                    nextJob?.downloadJob?.join()
                    downloadQueue.remove(nextJob)
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

}

class TaggedDownloadJob(val tag: String, val downloadJob: Job)

class DownloadJobSuperseededException(
    val newJob: Job,
    message: String = "The download for this tag was superseeded by a new job"
) : CancellationException(message)

class CannotDetermineBaseUrlException(message: String, cause: Throwable? = null) :
    Exception(message, cause)