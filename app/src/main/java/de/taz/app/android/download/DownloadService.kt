package de.taz.app.android.download

import android.content.Context
import android.system.ErrnoException
import androidx.lifecycle.MutableLiveData
import androidx.work.*
import de.taz.app.android.DOWNLOAD_MAX_CONNECTIONS_PER_ROUTE
import de.taz.app.android.PREFERENCES_DOWNLOADS
import de.taz.app.android.SETTINGS_DOWNLOAD_ONLY_WIFI
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
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.sentry.Sentry
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileNotFoundException
import java.util.*
import java.util.concurrent.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Mockable
@KtorExperimentalAPI
class DownloadService constructor(
    val applicationContext: Context,
    private val fileEntryRepository: FileEntryRepository,
    private val issueRepository: IssueRepository,
    private val apiService: ApiService,
    private val fileHelper: FileHelper,
    private val toastHelper: ToastHelper,
    private val httpClient: HttpClient
) {

    private constructor(applicationContext: Context) : this(
        applicationContext,
        FileEntryRepository.getInstance(applicationContext),
        IssueRepository.getInstance(applicationContext),
        ApiService.getInstance(applicationContext),
        FileHelper.getInstance(applicationContext),
        ToastHelper.getInstance(applicationContext),
        httpClient = HttpClient(CIO) {
            engine {
                pipelining = true
                endpoint {
                    maxConnectionsPerRoute = DOWNLOAD_MAX_CONNECTIONS_PER_ROUTE
                }
            }
        }
    )

    companion object : SingletonHolder<DownloadService, Context>(::DownloadService)

    private val downloadListMutex = Mutex()
    private val taggedDownloadMap = ConcurrentHashMap<String, TaggedDownloadJob>()
    private var fileDownloaderJob: Job? = null
    private val fileDownloadPriorityQueue = PriorityBlockingQueue<PrioritizedFileDownload>()
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
        isAutomaticDownload: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        ensureDownloadHelper()
        if (downloadableCollection.isDownloaded()) {
            val isComplete = fileHelper.ensureFileListIntegrity(downloadableCollection.getAllFiles())

            if (isComplete) {
                statusLiveData.postValue(DownloadStatus.done)
                return@withContext
            }
        }
        try {
            log.info("Schedule Download of ${downloadableCollection.getDownloadTag()}")
            statusLiveData.postValue(DownloadStatus.started)
            scheduleDownloadOrListenToRunning(
                downloadableCollection,
                isAutomaticDownload = isAutomaticDownload
            )
            statusLiveData.postValue(DownloadStatus.done)
            downloadableCollection.setDownloadDate(Date())
        } catch (e: Exception) {
            log.warn("Exception caught on ensureCollectionDownloaded(). Set state pending")
            statusLiveData.postValue(DownloadStatus.pending)
        }

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
        val dataService = DataService.getInstance(applicationContext)
        when (fileEntry.storageType) {
            StorageType.global -> {
                dataService.getAppInfo().globalBaseUrl
            }
            StorageType.resource -> {
                dataService.getResourceInfo().resourceBaseUrl
            }
            StorageType.issue -> {
                when (collection) {
                    is Moment -> if (collection.baseUrl.isNotEmpty()) { collection.baseUrl } else {
                        // TODO: We migrated baseUrl from Issue in earlier versions to make the Moment standalone. To monitor this volatile migration report any inconsistency
                        // Can be removed if no problem occurs
                        val hint = "Moment.baseUrl was not properly migrated for ${collection.getDownloadTag()}"
                        Sentry.captureMessage(hint)
                        log.warn(hint)
                        issueRepository.get(
                            IssueKey(
                                collection.issueFeedName,
                                collection.issueDate,
                                collection.issueStatus
                            )
                        )?.baseUrl ?: throw IllegalStateException("Could not determine base url for ${collection.getDownloadTag()}")
                    }
                    is IssueOperations-> collection.baseUrl
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
        baseUrl: String,
        force: Boolean = false
    ) {
        ensureDownloadHelper()
        // skip this if we have the correct version downloaded
        if (fileHelper.ensureFileIntegrity(fileToDownload.path, fileToDownload.sha256) && !force) {
            if (fileToDownload.getDownloadDate() == null) {
                fileToDownload.setDownloadDate(Date())
            }
            return
        }
        val response = downloadConnectionHelper.retryOnConnectivityFailure({
            toastHelper.showNoConnectionToast()
        }) {
            transformToConnectivityException {
                httpClient.get<HttpStatement>(
                    "$baseUrl/${fileToDownload.name}"
                ).execute()
            }
        }

        val channel = response.receive<ByteReadChannel>()
        when (response.status.value) {
            in 200..299 -> {
                handleResponse(channel, fileToDownload)
            }
            in 400..499 -> {
                log.warn("Download of ${fileToDownload.name} not successful ${response.status.value}")
                Sentry.captureMessage(response.readText())
                fileEntryRepository.resetDownloadDate(fileToDownload)
                throw ConnectivityException.ImplementationException(
                    "Response code ${response.status.value} while trying to download ${fileToDownload.name}",
                    null,
                    response
                )
            }
            in 500..599 -> {
                log.warn("Download of ${fileToDownload.name} not successful ${response.status.value}")
                Sentry.captureMessage(response.readText())
                throw ConnectivityException.ServerUnavailableException("Response code ${response.status.value} while trying to download ${fileToDownload.name}")
            }
            else -> {
                log.warn("Unexpected code ${response.status.value} for  ${fileToDownload.name}")
                Sentry.captureMessage(response.readText())
            }
        }

    }


    /**
     * save server response to file, calculate sha and compare
     */
    private suspend fun handleResponse(
        channel: ByteReadChannel, downloadedFile: FileEntry
    ) = withContext(NonCancellable) {

        try {
            // ensure folders are created
            fileHelper.createFileDirs(downloadedFile)
            val sha256 = fileHelper.writeFile(downloadedFile, channel)
            if (sha256 == downloadedFile.sha256) {
                fileEntryRepository.setDownloadDate(downloadedFile, Date())
            } else {
                val hint = "SHA-256 mismatch on ${downloadedFile.name}"
                log.warn(hint)
                Sentry.captureMessage(hint)
                fileEntryRepository.resetDownloadDate(
                        downloadedFile
                )
            }
        } catch (e: Exception) {
            val hint = "Couldn't write file ${downloadedFile.name}"
            log.warn(hint)
            Sentry.captureException(e, hint)
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
        val downloadJob = downloadListMutex.withLock {
            val newOrExistingJob = taggedDownloadMap[tag] ?: scheduleDownload(
                    downloadableCollection,
                    isAutomaticDownload
                )
            taggedDownloadMap[tag] = newOrExistingJob
            newOrExistingJob
        }

        suspendCoroutine<Unit> { continuation ->
            downloadJob.waiters.offer(continuation)
            // If the download collection was empty no download job will ever notify completion, so we do it right here
            CoroutineScope(Dispatchers.IO).launch { maybeNotifyCompletion(downloadJob.tag) }
        }


    }

    private suspend fun maybeNotifyCompletion(tag: String) = downloadListMutex.withLock {
        taggedDownloadMap[tag]?.let { taggedDownload ->
            if (taggedDownload.fileDownloadJobs.all { it.job.isCompleted }) {
                val secondsTaken = (Date().time - taggedDownload.startDate.time).toFloat() / 1000
                log.debug("It took $secondsTaken seconds for ${taggedDownload.tag} to download all its assets (DownloadId: ${taggedDownload.downloadId}")
                taggedDownload.downloadId?.let {
                    CoroutineScope(Dispatchers.IO).launch {
                        notifyIssueDownloadStop(it, secondsTaken)
                    }
                }

                taggedDownloadMap.remove(tag)
                taggedDownload.waiters.forEach {
                    try {
                        it.resume(Unit)
                    } catch (e: IllegalStateException) {
                        // If already resumed we can ignore this
                    }
                }
            }
        }
    }

    private suspend fun scheduleDownload(
        downloadableCollection: DownloadableCollection,
        isAutomaticDownload: Boolean = false
    ): TaggedDownloadJob =
        withContext(Dispatchers.IO) {
            log.info("Start download of ${downloadableCollection.getDownloadTag()}")
            val start = Date()
            val downloadId = if (downloadableCollection is Issue) {
                val downloadId =
                    notifyIssueDownloadStart(downloadableCollection, isAutomaticDownload)
                downloadId
            } else {
                null
            }

            val tag = downloadableCollection.getDownloadTag()

            val filesToDownload =
                fileHelper.getCorruptedFilesFromList(downloadableCollection.getAllFiles())

            val fileDownloadJobs = filesToDownload
                .map {
                    CoroutineScope(Dispatchers.IO).launch(start = CoroutineStart.LAZY) {
                        log.verbose("Downloading ${it.name} for $tag")
                        downloadAndSaveFile(
                            it,
                            determineBaseUrl(downloadableCollection, it)
                        )
                    }
                }.map {
                    if (downloadableCollection is Issue) {
                        PrioritizedFileDownload(it, DownloadPriority.Normal, tag)
                    } else {
                        PrioritizedFileDownload(it, DownloadPriority.High, tag)
                    }
                }

            fileDownloadPriorityQueue.addAll(fileDownloadJobs)
            TaggedDownloadJob(
                tag,
                fileDownloadJobs,
                start,
                downloadId,
                ConcurrentLinkedQueue<Continuation<Unit>>()
            ).also {
                CoroutineScope(Dispatchers.IO).launch { ensureFileDownloaderRunning() }
            }
        }

    private suspend fun ensureDownloadHelper() {
        if (!::downloadConnectionHelper.isInitialized) {
            downloadConnectionHelper = DownloadConnectionHelper(
                DataService.getInstance(applicationContext).getAppInfo().globalBaseUrl
            )
            log.debug("Initialized downloadConnectionHelper")
        }
    }

    private suspend fun ensureFileDownloaderRunning() {

        fileDownloaderJob?.join()
        fileDownloaderJob = CoroutineScope(Dispatchers.Default).launch {
            while (fileDownloadPriorityQueue.isNotEmpty()) {
                fileDownloadPriorityQueue.poll()?.let { download ->
                    CoroutineScope(Dispatchers.IO).launch(NonCancellable) {
                        try {
                            download.job.join()
                        } finally {
                            download.collectionTag?.let { maybeNotifyCompletion(it) }
                        }
                    }
                }
            }
        }
    }

    /**
     * get Constraints for [WorkRequest] of [WorkManager]
     */
    fun getBackgroundDownloadConstraints(): Constraints {
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
     * download new issue in background
     */
    fun scheduleNewestIssueDownload(
        tag: String,
        polling: Boolean = false,
        delay: Long = 0L
    ): Operation? {
        val requestBuilder =
            OneTimeWorkRequest.Builder(IssueDownloadWorkManagerWorker::class.java)
                .setConstraints(getBackgroundDownloadConstraints())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_SCHEDULE_NEXT to polling
                    )
                )
                .addTag(tag)

        return WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            tag,
            ExistingWorkPolicy.KEEP,
            requestBuilder.build()
        )
    }
}

/**
 * Priorities for scheduling downloads
 * Ordinal of enum is the natural comparable - so first item in list has highest prio, last item least
 */
enum class DownloadPriority {
    High,
    Normal
}

data class PrioritizedFileDownload(
    val job: Job,
    val priority: DownloadPriority,
    val collectionTag: String?
) :
    Comparable<PrioritizedFileDownload> {
    override fun compareTo(other: PrioritizedFileDownload): Int {
        return priority.compareTo(other.priority)
    }
}

class TaggedDownloadJob(
    val tag: String,
    val fileDownloadJobs: List<PrioritizedFileDownload>,
    val startDate: Date,
    val downloadId: String?,
    val waiters: ConcurrentLinkedQueue<Continuation<Unit>>
)

class CannotDetermineBaseUrlException(message: String, cause: Throwable? = null) :
    Exception(message, cause)