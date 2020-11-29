package de.taz.app.android.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.download.DownloadService
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.SingletonHolder
import io.ktor.util.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class LiveDataWithReferenceCount<T>(
    val liveData: LiveData<T>,
    val referenceCount: AtomicInteger = AtomicInteger(0)
)

/**
 * A central service providing data intransparent if from cache or remotely fetched
 */
@Mockable
class DataService(private val applicationContext: Context) {
    companion object : SingletonHolder<DataService, Context>(::DataService)

    private val apiService = ApiService.getInstance(applicationContext)
    private val fileHelper = FileHelper.getInstance(applicationContext)

    private val appInfoRepository = AppInfoRepository.getInstance(applicationContext)
    private val issueRepository = IssueRepository.getInstance(applicationContext)
    private val viewerStateRepository = ViewerStateRepository.getInstance(applicationContext)
    private val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)
    private val downloadService = DownloadService.getInstance(applicationContext)
    private val feedRepository = FeedRepository.getInstance(applicationContext)
    private val authHelper = AuthHelper.getInstance(applicationContext)

    private val downloadLiveDataMap: ConcurrentHashMap<String, LiveDataWithReferenceCount<DownloadStatus>> =
        ConcurrentHashMap()

    /**
     * Returns an [IssueKey] derrived by an [issuePublication] determined by application state
     * The missing [IssueStatus] will be determined by
     * 1. If I have an [Issue] cached in DB with [IssueStatus.regular] status will be [IssueStatus.regular]
     * 2. If [AuthHelper.eligibleIssueStatus] is [IssueStatus.regular] status will be [IssueStatus.regular]
     * 3. In any other case the [IssueStatus] will be [IssueStatus.public]
     * @param issuePublication Publication (feed and date)
     * @return [IssueKey] with derrived [IssueStatus]
     */
    suspend fun determineIssueKey(issuePublication: IssuePublication) =
        withContext(Dispatchers.IO) {
            val regularKey =
                IssueKey(issuePublication.feed, issuePublication.date, IssueStatus.regular)
            val publicKey =
                IssueKey(issuePublication.feed, issuePublication.date, IssueStatus.public)
            val issueKey =
                if (isCached(regularKey) || authHelper.eligibleIssueStatus == IssueStatus.regular) {
                    regularKey
                } else {
                    publicKey
                }
            issueKey
        }

    suspend fun isCached(issueKey: IssueKey): Boolean = withContext(Dispatchers.IO) {
        issueRepository.exists(issueKey)
    }

    /**
     * This function returns IssueStub from a given [issuePublication]. If [allowCache] is true a local copy
     * will be returned if existent. If not server will be requested.
     * By specifying only an [IssuePublication] you might receive the resulting [IssueStub] might have any
     * [IssueStatus] depending on application state
     *
     * @param issuePublication Publication (feed and date)
     * @param allowCache checks if issue already exists
     * @param retryOnFailure when hitting server retry on recoverable (connection-) issues indefinitely
     */
    suspend fun getIssueStub(
        issuePublication: IssuePublication,
        allowCache: Boolean = true,
        retryOnFailure: Boolean = false
    ): IssueStub? =
        withContext(Dispatchers.IO) {
            getIssueStub(
                determineIssueKey(issuePublication),
                allowCache = allowCache,
                retryOnFailure = retryOnFailure
            )
        }

    /**
     * This function returns [IssueStub] from a given [issuePublication]. If [allowCache] is true a local copy
     * will be returned if existent. If not server will be requested.
     * By specifying only an [IssuePublication] you might receive the resulting [Issue] might have any
     * [IssueStatus] depending on application state
     *
     * @param issuePublication Publication (feed and date)
     * @param allowCache checks if issue already exists
     * @param retryOnFailure when hitting server retry on recoverable (connection-) issues indefinitely
     */
    suspend fun getIssue(
        issuePublication: IssuePublication,
        allowCache: Boolean = true,
        retryOnFailure: Boolean = false
    ): Issue? =
        withContext(Dispatchers.IO) {
            getIssue(
                determineIssueKey(issuePublication),
                allowCache = allowCache,
                retryOnFailure = retryOnFailure
            )
        }

    /**
     * This function returns IssueStub from a given [issueKey].
     * ATTENTION! The issue returned from the called getIssue function has the status depending
     * of the AuthStatus (logged in or not). Whereas a cached result always will return the IssueStatus
     * specified in the [issueKey].
     *
     * @param issueKey Key of feed, date and status
     * @param allowCache checks if issue already exists
     * @param retryOnFailure calls getIssue again if unsuccessful
     */
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

    /**
     * This function returns [Issue] from a given [issueKey].
     * ATTENTION! The issue returned from the called getIssue function has the status depending
     * of the AuthStatus (logged in or not). Whereas a cached result always will return the IssueStatus
     * specified in the [issueKey].
     *
     * @param issueKey Key of feed, date and status
     * @param allowCache checks if issue already exists
     * @param retryOnFailure calls getIssue again if unsuccessful
     */
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

    suspend fun getLastDisplayableOnIssue(issueKey: IssueKey): String? = withContext(Dispatchers.IO) {
        issueRepository.getLastDisplayable(issueKey)
    }

    suspend fun saveLastDisplayableOnIssue(issueKey: IssueKey, displayableName: String) = withContext(Dispatchers.IO) {
        issueRepository.saveLastDisplayable(issueKey, displayableName)
    }

    suspend fun getViewerStateForDisplayable(displayableName: String): ViewerState? = withContext(Dispatchers.IO) {
        viewerStateRepository.get(displayableName)
    }

    suspend fun saveViewerStateForDisplayable(displayableName: String, scrollPosition: Int) = withContext(Dispatchers.IO) {
        viewerStateRepository.save(
            displayableName,
            scrollPosition
        )
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

    suspend fun getMoment(
        issueKey: IssueKey,
        allowCache: Boolean = true,
        retryOnFailure: Boolean = false
    ): Moment? =
        withContext(Dispatchers.IO) {
            if (allowCache) {
                momentRepository.get(issueKey)?.let { return@withContext it }
            }
            val moment = if (retryOnFailure) {
                apiService.retryOnConnectionFailure {
                    apiService.getMomentByFeedAndDate(
                        issueKey.feedName,
                        simpleDateFormat.parse(issueKey.date)!!
                    )
                }
            } else {
                apiService.getMomentByFeedAndDate(
                    issueKey.feedName,
                    simpleDateFormat.parse(issueKey.date)!!
                )
            }

            moment?.let {
                momentRepository.save(moment)
            }
            moment
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
        withDownloadLiveData(collection as ObservableDownload) { liveData ->
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
        downloadService.downloadAndSaveFile(fileEntry, baseUrl)
    }

    suspend fun ensureDeleted(collection: DownloadableCollection) {
        ensureDeletedFiles(collection)
        if (collection is Issue) {
            issueRepository.delete(collection)
        }
    }

    suspend fun ensureDeletedFiles(collection: DownloadableCollection) {
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


    suspend fun getFeedByName(
        name: String,
        allowCache: Boolean = true,
        retryOnFailure: Boolean = false
    ): Feed? =
        withContext(Dispatchers.IO) {
            if (allowCache) {
                feedRepository.get(name)?.let {
                    return@withContext it
                }
            }
            val feed = if (retryOnFailure) {
                apiService.retryOnConnectionFailure {
                    apiService.getFeedByName(name)
                }
            } else {
                apiService.getFeedByName(name)
            }
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

    suspend fun isIssueDownloaded(issueKey: IssueKey): Boolean = withContext(Dispatchers.IO) {
        issueRepository.isDownloaded(issueKey)
    }

    /**
     * Refresh the the Feed with [feedName] and return an [Issue] if a new issue date was detected
     * @param feedName to refresh
     */
    suspend fun refreshFeedAndGetIssueIfNew(feedName: String): Issue? =
        withContext(Dispatchers.IO) {
            val cachedFeed = getFeedByName(feedName)
            val refreshedFeed = getFeedByName(feedName, allowCache = false)
            val newsestIssueDate = refreshedFeed?.publicationDates?.getOrNull(0)
            newsestIssueDate?.let {
                if (newsestIssueDate != cachedFeed?.publicationDates?.getOrNull(0)) {
                    getIssue(IssuePublication(feedName, simpleDateFormat.format(it)))
                } else {
                    null
                }
            }
        }

    /**
     * Gets or creates a LiveData observing the download progress of [downloadableStub].
     * ATTENTION: As any call to [ensureDownloaded] and [ensureDeletedFiles] will attempt to cleanup non-observed LiveDatas
     * you only should access it via [withDownloadLiveData] that manages locking and reference count
     */
    private suspend fun getDownloadLiveData(observableDownload: ObservableDownload): LiveDataWithReferenceCount<DownloadStatus> =
        withContext(Dispatchers.IO) {
            val tag = observableDownload.getDownloadTag()

            log.verbose("Requesting livedata for $tag")
            downloadLiveDataMap[tag] ?: run {
                val status = when (observableDownload) {
                    is IssueKey -> if (issueRepository.isDownloaded(observableDownload)) DownloadStatus.done else DownloadStatus.pending
                    is DownloadableCollection -> observableDownload.getDownloadDate(
                        applicationContext
                    )
                        ?.let { DownloadStatus.done } ?: DownloadStatus.pending
                    else -> DownloadStatus.pending
                }
                val downloadLiveData = LiveDataWithReferenceCount(MutableLiveData(status))
                downloadLiveDataMap[tag] = downloadLiveData
                log.verbose("Created livedata for $tag")
                downloadLiveData
            }
        }

    /**
     * Refresh the the Feed with [feedName] and return an [Issue] if a new issue date was detected
     * @param feedName to refresh
     */
    suspend fun refreshFeedAndGetIssueIfNew(feedName: String): Issue? = withContext(Dispatchers.IO) {
        val cachedFeed = getFeedByName(feedName)
        val refreshedFeed = getFeedByName(feedName, allowCache = false)
        val newestIssue = refreshedFeed?.publicationDates?.getOrNull(0)
        newestIssue?.let {
            if (newestIssue != cachedFeed?.publicationDates?.getOrNull(0)) {
                getIssue(IssueKey(feedName, simpleDateFormat.format(it), authHelper.eligibleIssueStatus))
            } else {
                null
            }
        }
    }


    /**
     * If you want to listen in to a download state you'll have to do it with this wrapper function.
     * There is some racyness involved between creating the livedata and observing to it and possibly cleaning it up.
     * Therefore this service counts references of routines accessing the livedata so it doesn't get cleaned up
     * before we manage to .observe it. Cleanup respects either actual observers or active references to retain a LiveData.
     *
     * @param observableDownload: Object implementing [ObservableDownload]
     * @param block: Block executed where the LiveData is definitely safe of cleanup
     */
    suspend fun withDownloadLiveData(
        observableDownload: ObservableDownload,
        block: suspend (LiveData<DownloadStatus>) -> Unit
    ) {

        val (liveData, referenceCount) = getDownloadLiveData(observableDownload)
        referenceCount.incrementAndGet()

        try {
            block(liveData)
        } finally {
            referenceCount.decrementAndGet()

        }
    }
}
