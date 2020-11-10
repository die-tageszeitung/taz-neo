package de.taz.app.android.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.DownloadableStub
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.download.DownloadService
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.HashMap

data class LiveDataWithReferenceCount<T>(
    val liveData: LiveData<T>,
    val referenceCount: AtomicInteger = AtomicInteger(0)
)

/**
 * A central service providing data intransparent if from cache or remotely fetched
 */
class DataService(applicationContext: Context) {
    companion object : SingletonHolder<DataService, Context>(::DataService)

    private val apiService = ApiService.getInstance(applicationContext)
    private val fileHelper = FileHelper.getInstance(applicationContext)

    private val appInfoRepository = AppInfoRepository.getInstance(applicationContext)
    private val issueRepository = IssueRepository.getInstance(applicationContext)
    private val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)
    private val downloadService = DownloadService.getInstance(applicationContext)
    private val feedRepository = FeedRepository.getInstance(applicationContext)

    private val downloadLiveDataMap: HashMap<String, LiveDataWithReferenceCount<DownloadStatus>> =
        HashMap()

    suspend fun getIssueStub(
        issueKey: IssueKey,
        allowCache: Boolean = true,
        retryOnFailure: Boolean = false
    ): IssueStub? =
        withContext(Dispatchers.IO) {
            if (allowCache) {
                issueRepository.getStub(issueKey)?.let { return@withContext it } ?: run {
                    log.info("Cache miss on $issueKey")
                }
            }
            getIssue(issueKey, allowCache, retryOnFailure = retryOnFailure)?.let(::IssueStub)
        }

    suspend fun getIssue(
        issueKey: IssueKey,
        allowCache: Boolean = true,
        forceUpdate: Boolean = false,
        retryOnFailure: Boolean = false
    ): Issue? = withContext(Dispatchers.IO) {
        if (allowCache) {
            issueRepository.get(issueKey)?.let { return@withContext it } ?: run {
                log.info("Cache miss on $issueKey")
            }
        }
        val issue = if (retryOnFailure) {
            apiService.retryOnConnectionFailure {
                apiService.getIssueByKey(issueKey)
            }
        } else {
            apiService.getIssueByKey(issueKey)
        }
        if (forceUpdate) {
            return@withContext issueRepository.save(issue)
        } else {
            return@withContext issueRepository.saveIfNotExistOrOutdated(issue)
        }

    }


    suspend fun getIssueStubs(
        fromDate: Date = Date(),
        status: IssueStatus,
        limit: Int = 1,
        allowCache: Boolean = true
    ): List<IssueStub> = withContext(Dispatchers.IO) {
        if (allowCache) {
            val issues = issueRepository.getLatestIssueStubsByDateAndStatus(
                simpleDateFormat.format(fromDate), status, limit
            )
            if (issues.size == limit) {
                return@withContext issues
            }
        }
        val feedNames = feedRepository.getAll().map { it.name }
        return@withContext feedNames
            .map { feedName ->
                apiService.getIssuesByFeedAndDate(feedName, fromDate, limit)
            }
            .flatten()
            .map(issueRepository::saveIfNotExistOrOutdated)
            .map(::IssueStub)
    }

    suspend fun getIssueStubsByFeed(
        fromDate: Date = Date(),
        feedNames: List<String>,
        limit: Int = 1,
        allowCache: Boolean = true,
        retryOnFailure: Boolean = false
    ): List<IssueStub> = withContext(Dispatchers.IO) {
        if (allowCache) {
            val issues = issueRepository.getLatestIssueStubsByFeedAndDate(
                simpleDateFormat.format(fromDate), feedNames, limit
            )
            if (issues.size == limit) {
                return@withContext issues
            }
        }
        val issues = if (retryOnFailure) {
            apiService.retryOnConnectionFailure {
                feedNames.map { feedName ->
                    apiService.getIssuesByFeedAndDate(feedName, fromDate, limit)
                }.flatten()
            }
        } else {
            feedNames.map { feedName ->
                apiService.getIssuesByFeedAndDate(feedName, fromDate, limit)
            }.flatten()
        }

        issueRepository.saveIfNotExistOrOutdated(issues).map(::IssueStub)
    }

    suspend fun getAppInfo(allowCache: Boolean = true, retryOnFailure: Boolean = false): AppInfo =
        withContext(Dispatchers.IO) {
            if (allowCache) {
                appInfoRepository.get()?.let {
                    return@withContext it
                } ?: run { log.info("Cache miss on getAppInfo") }
            }
            val appInfo = if (retryOnFailure) {
                apiService.retryOnConnectionFailure {
                    apiService.getAppInfo()
                }
            } else {
                apiService.getAppInfo()
            }
            appInfoRepository.save(appInfo)
            appInfo
        }

    suspend fun getMoment(issueKey: IssueKey, allowCache: Boolean = true): Moment? =
        withContext(Dispatchers.IO) {
            return@withContext momentRepository.get(issueKey)
        }


    suspend fun getResourceInfo(
        allowCache: Boolean = true,
        retryOnFailure: Boolean = false
    ): ResourceInfo = withContext(Dispatchers.IO) {
        if (allowCache) {
            resourceInfoRepository.getNewest()?.let {
                return@withContext it
            }
        }
        val resourceInfo = if (retryOnFailure) {
            apiService.retryOnConnectionFailure {
                apiService.getResourceInfo()
            }
        } else {
            apiService.getResourceInfo()
        }
        resourceInfoRepository.save(resourceInfo)
        return@withContext resourceInfo
    }

    suspend fun ensureDownloaded(
        collection: DownloadableCollection,
        isAutomaticDownload: Boolean = false
    ) = withContext(Dispatchers.IO) {
        withDownloadLiveData(collection) { liveData ->
            downloadService.ensureCollectionDownloaded(
                collection,
                liveData as MutableLiveData<DownloadStatus>,
                isAutomaticDownload = isAutomaticDownload
            )
        }

        cleanUpLiveData()
    }

    /**
     * Iterate the livedata map to see if we can clean up LiveData that have neither references nor observers
     */
    private fun cleanUpLiveData() = synchronized(downloadLiveDataMap) {
        downloadLiveDataMap.entries.removeAll { (_, liveDataWithReferenceCount) ->
            liveDataWithReferenceCount.referenceCount.get() <= 0 && !liveDataWithReferenceCount.liveData.hasObservers()
        }
    }


    suspend fun ensureDownloaded(fileEntry: FileEntry, baseUrl: String) {
        if (!fileHelper.ensureFileIntegrity(fileEntry.path, fileEntry.sha256)) {
            downloadService.downloadAndSaveFile(fileEntry, baseUrl)
        }
    }

    suspend fun ensureDeleted(collection: DownloadableCollection) {
        withDownloadLiveData(collection) { liveData ->
            collection.setDownloadDate(null)
            (liveData as MutableLiveData<DownloadStatus>).postValue(DownloadStatus.pending)
        }

        collection.getAllFiles().forEach { fileHelper.deleteFile(it) }
        cleanUpLiveData()
    }

    suspend fun sendNotificationInfo(
        token: String,
        oldToken: String? = null,
        retryOnFailure: Boolean = false
    ): Boolean =
        withContext(Dispatchers.IO) {
            log.info("Sending notification info")
            if (retryOnFailure) {
                apiService.retryOnConnectionFailure {
                    apiService.sendNotificationInfo(token, oldToken)
                }
            } else {
                apiService.sendNotificationInfo(token, oldToken)
            }
        }


    suspend fun getFeedByName(name: String, allowCache: Boolean = true): Feed? =
        withContext(Dispatchers.IO) {
            if (allowCache) {
                feedRepository.get(name)?.let {
                    return@withContext it
                }
            }
            val feed = apiService.getFeedByName(name)
            feed?.let {
                feedRepository.save(feed)
            }
            feed
        }

    suspend fun getFeeds(allowCache: Boolean = true): List<Feed> = withContext(Dispatchers.IO) {
        if (allowCache) {
            feedRepository.getAll().let {
                if (it.isNotEmpty()) return@withContext it
            }
        }
        val feeds = apiService.getFeeds()
        feedRepository.save(feeds)
        feeds
    }

    /**
     * Gets or creates a LiveData observing the download progress of [downloadableStub].
     * ATTENTION: As any call to [ensureDownloaded] and [ensureDeleted] will attempt to cleanup non-observed LiveDatas
     * you only should access it via [withDownloadLiveData] that manages locking and reference count
     */
    private suspend fun getDownloadLiveData(downloadableStub: DownloadableStub): LiveDataWithReferenceCount<DownloadStatus> {
        val tag = downloadableStub.getDownloadTag()
        val status = withContext(Dispatchers.IO) {
            downloadableStub.getDownloadDate()?.let { DownloadStatus.done }
        }
            ?: DownloadStatus.pending
        log.verbose("Requesting livedata for $tag")
        return downloadLiveDataMap[tag] ?: run {
            val downloadLiveData = LiveDataWithReferenceCount(MutableLiveData(status))
            downloadLiveDataMap[tag] = downloadLiveData
            log.verbose("Created livedata for $tag")
            downloadLiveData
        }

    }

    /**
     * If you want to listen in to a download state you'll have to do it with this wrapper function.
     * There is some racyness involved between creating the livedata and observing to it and possibly cleaning it up.
     * Therefore this service counts references of routines accessing the livedata so it doesn't get cleaned up
     * before we manage to .observe it. Cleanup respects either actual observers or active references to retain a LiveData.
     *
     * @param downloadableStub: Object implementing [DownloadableStub]
     * @param block: Block executed where the LiveData is definitely safe of cleanup
     */
    fun withDownloadLiveDataSync(
        downloadableStub: DownloadableStub,
        block: (LiveData<DownloadStatus>) -> Unit
    ) {
        val (liveData, referenceCount) = synchronized(downloadLiveDataMap) {
            val (liveData, referenceCount) = runBlocking { getDownloadLiveData(downloadableStub) }
            referenceCount.incrementAndGet()
            liveData to referenceCount
        }
        try {
            block(liveData)
        } finally {
            synchronized(downloadLiveDataMap) {
                referenceCount.decrementAndGet()
            }
        }
    }

    /**
     *
     * Wraps [withDownloadLiveDataSync] to work with suspended blocks
     *
     * @param downloadableStub: Object implementing [DownloadableStub]
     * @param block: Block executed where the LiveData is definitely safe of cleanup
     */
    suspend fun withDownloadLiveData(
        downloadableStub: DownloadableStub,
        block: suspend (LiveData<DownloadStatus>) -> Unit
    ) {
        withDownloadLiveDataSync(downloadableStub) { liveData -> runBlocking { block(liveData) } }
    }
}
