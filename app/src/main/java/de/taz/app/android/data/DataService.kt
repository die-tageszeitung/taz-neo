package de.taz.app.android.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.SETTINGS_GENERAL_KEEP_ISSUES
import de.taz.app.android.SETTINGS_GENERAL_KEEP_ISSUES_DEFAULT
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.download.DownloadService
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.SharedPreferenceStringLiveData
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val storageService = StorageService.getInstance(applicationContext)

    private val appInfoRepository = AppInfoRepository.getInstance(applicationContext)
    private val issueRepository = IssueRepository.getInstance(applicationContext)
    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val viewerStateRepository = ViewerStateRepository.getInstance(applicationContext)
    private val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)
    private val pageRepository = PageRepository.getInstance(applicationContext)
    private val downloadService = DownloadService.getInstance(applicationContext)
    private val feedRepository = FeedRepository.getInstance(applicationContext)
    private val authHelper = AuthHelper.getInstance(applicationContext)
    private val toastHelper = ToastHelper.getInstance(applicationContext)

    private val downloadLiveDataLock = Mutex()

    private val downloadLiveDataMap: ConcurrentHashMap<String, LiveDataWithReferenceCount<DownloadStatus>> =
        ConcurrentHashMap()
    private val sharedPrefs =
        applicationContext.getSharedPreferences(PREFERENCES_GENERAL, Context.MODE_PRIVATE)

    private val maxStoredIssueNumberLiveData =
        SharedPreferenceStringLiveData(
            sharedPrefs,
            SETTINGS_GENERAL_KEEP_ISSUES,
            SETTINGS_GENERAL_KEEP_ISSUES_DEFAULT.toString()
        )
    private val downloadIssueNumberLiveData = issueRepository.getDownloadedIssuesCountLiveData()
    private val ensureCountLock = Mutex()

    init {
        maxStoredIssueNumberLiveData.observeForever {
            CoroutineScope(Dispatchers.IO).launch {
                ensureIssueCount()
            }
        }
        downloadIssueNumberLiveData.observeForever {
            CoroutineScope(Dispatchers.IO).launch {
                ensureIssueCount()
            }
        }
    }

    private suspend fun ensureIssueCount() = ensureCountLock.withLock {
        runIfNotNull(
            downloadIssueNumberLiveData.value,
            maxStoredIssueNumberLiveData.value.toIntOrNull()
        ) { downloaded, max ->
            var downloadedCounter = downloaded
            while (downloadedCounter > max) {
                runBlocking {
                    issueRepository.getEarliestDownloadedIssueStub()?.let {
                        ensureDeletedFiles(it.getIssue())
                    }
                    downloadedCounter--
                }
            }
        }
    }

    /**
     * This function returns IssueStub from a given [issuePublication].
     * ATTENTION! The issue returned from the called getIssue function has the status depending
     * of the AuthStatus (logged in or not).
     *
     * @param issuePublication Key of feed and date
     * @param allowCache checks if issue already exists
     * @param retryOnFailure calls getIssue again if unsuccessful
     */
    suspend fun getIssueStub(
        issuePublication: IssuePublication,
        allowCache: Boolean = true,
        retryOnFailure: Boolean = false,
        cacheWithPages: Boolean = false
    ): IssueStub =
        withContext(Dispatchers.IO) {
            val regularKey = IssueKey(issuePublication, IssueStatus.regular)
            val publicKey = IssueKey(issuePublication, IssueStatus.public)

            if (allowCache) {
                issueRepository.getStub(regularKey)?.let {
                    if (!cacheWithPages && it.isDownloaded() ||
                        IssueWithPages(it.getIssue()).isDownloaded()
                    ) return@withContext it
                }
                if (authHelper.eligibleIssueStatus != IssueStatus.regular) {
                    issueRepository.getStub(publicKey)?.let { return@withContext it }
                }
                log.info("Cache miss on $issuePublication")
            }
            getIssue(
                issuePublication,
                allowCache,
                retryOnFailure = retryOnFailure
            ).let(::IssueStub)
        }

    /**
     * This function is an overload for [getIssue] to avoid having a suspend callback in it's signature to
     * be compatible with mockito in tests
     *
     * @param issuePublication Key of feed and date
     * @param allowCache checks if issue already exists
     * @param forceUpdate Always update the database cached issue after download
     * @param retryOnFailure calls getIssue again if unsuccessful
     */
    suspend fun getIssue(
        issuePublication: IssuePublication,
        allowCache: Boolean = true,
        forceUpdate: Boolean = false,
        retryOnFailure: Boolean = false,
        cacheWithPages: Boolean = false
    ): Issue = withContext(Dispatchers.IO) {
        getIssue(issuePublication, allowCache, forceUpdate, retryOnFailure, cacheWithPages) {}
    }

    /**
     * This function returns [Issue] from a given [issuePublication].
     * ATTENTION! The issue returned from the called getIssue function has the status depending
     * of the AuthStatus (logged in or not) or might be regular in any case if the taz decides to
     * issue a "demo" issue (meaning unauthenticated users will also recieve regular issues).
     * If we have a cached version of a "regular" issue available we always default to it.
     *
     * @param issuePublication Key of feed and date
     * @param allowCache checks if issue already exists
     * @param forceUpdate Always update the database cached issue after download
     * @param retryOnFailure calls getIssue again if unsuccessful
     * @param onConnectionFailure callback to handle connection failures
     */
    suspend fun getIssue(
        issuePublication: IssuePublication,
        allowCache: Boolean = true,
        forceUpdate: Boolean = false,
        retryOnFailure: Boolean = false,
        cacheWithPages: Boolean = false,
        onConnectionFailure: suspend () -> Unit = {}
    ): Issue = withContext(Dispatchers.IO) {
        val regularKey = IssueKey(issuePublication, IssueStatus.regular)
        val publicKey = IssueKey(issuePublication, IssueStatus.public)

        if (allowCache) {
            issueRepository.get(regularKey)?.let {
                if ((it.isDownloaded() && !cacheWithPages) || IssueWithPages(it).isDownloaded())
                    return@withContext it
            }
            if (authHelper.eligibleIssueStatus != IssueStatus.regular) {
                issueRepository.get(publicKey)?.let { return@withContext it }
            }
            log.info("Cache miss on $issuePublication")
        }
        val issue = if (retryOnFailure) {
            apiService.retryOnConnectionFailure({
                onConnectionFailure()
            }) {
                apiService.getIssueByPublication(issuePublication)
            }
        } else {
            apiService.getIssueByPublication(issuePublication)
        }
        if (forceUpdate) {
            return@withContext issueRepository.save(issue)
        } else {
            return@withContext issueRepository.saveIfNotExistOrOutdated(issue)
        }

    }

    suspend fun getLastDisplayableOnIssue(issueKey: IssueKey): String? =
        withContext(Dispatchers.IO) {
            issueRepository.getLastDisplayable(issueKey)
        }

    suspend fun saveLastDisplayableOnIssue(issueKey: IssueKey, displayableName: String) =
        withContext(Dispatchers.IO) {
            issueRepository.saveLastDisplayable(issueKey, displayableName)
        }

    suspend fun getViewerStateForDisplayable(displayableName: String): ViewerState? =
        withContext(Dispatchers.IO) {
            viewerStateRepository.get(displayableName)
        }

    suspend fun saveViewerStateForDisplayable(displayableName: String, scrollPosition: Int) =
        withContext(Dispatchers.IO) {
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
        issuePublication: IssuePublication,
        allowCache: Boolean = true,
        retryOnFailure: Boolean = false
    ): Moment? =
        withContext(Dispatchers.IO) {
            val regularKey = IssueKey(issuePublication, IssueStatus.regular)
            val publicKey = IssueKey(issuePublication, IssueStatus.public)

            if (allowCache) {
                momentRepository.get(regularKey)?.let { return@withContext it }
                if (authHelper.eligibleIssueStatus != IssueStatus.regular) {
                    momentRepository.get(publicKey)?.let { return@withContext it }
                }
                log.info("Cache miss on $issuePublication")
            }
            val moment = if (retryOnFailure) {
                apiService.retryOnConnectionFailure {
                    apiService.getMomentByFeedAndDate(
                        issuePublication.feed,
                        simpleDateFormat.parse(issuePublication.date)!!
                    )
                }
            } else {
                apiService.getMomentByFeedAndDate(
                    issuePublication.feed,
                    simpleDateFormat.parse(issuePublication.date)!!
                )
            }

            moment?.let {
                momentRepository.save(moment)
            }
            moment
        }

    suspend fun getFrontPage(
        issuePublication: IssuePublication,
        allowCache: Boolean = true,
        retryOnFailure: Boolean = false
    ): Page? =
        withContext(Dispatchers.IO) {
            val issueKey = IssueKey(issuePublication, IssueStatus.regular)

            if (allowCache) {
                pageRepository.getFrontPage(issueKey)?.let {
                    return@withContext it
                }
            }
            val page = if (retryOnFailure) {
                apiService.retryOnConnectionFailure {
                    apiService.getFrontPageByFeedAndDate(
                        issuePublication.feed,
                        simpleDateFormat.parse(issuePublication.date)!!
                    )
                }
            } else {
                apiService.getFrontPageByFeedAndDate(
                    issuePublication.feed,
                    simpleDateFormat.parse(issuePublication.date)!!
                )
            }

            page?.let {
                pageRepository.save(page, issueKey)
            }
            page
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
            apiService.retryOnConnectionFailure({
                toastHelper.showNoConnectionToast()
            }) {
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
        isAutomaticDownload: Boolean = false,
        skipIntegrityCheck: Boolean = false,
        onConnectionFailure: suspend () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        withDownloadLiveData(collection as ObservableDownload) { liveData ->
            // If we download issues we want to refresh metadata, as they might get stale quickly
            val refreshedCollection = if (
                !collection.isDownloaded()
            ) {
                when (collection) {
                    is Issue -> getIssue(
                        IssuePublication(collection.issueKey),
                        allowCache = false,
                        retryOnFailure = true
                    )
                    is IssueWithPages -> IssueWithPages(
                        getIssue(
                            IssuePublication(collection.issueKey),
                            allowCache = false,
                            retryOnFailure = true
                        )
                    )
                    else -> collection
                }
            } else {
                collection
            }

            downloadService.ensureCollectionDownloaded(
                refreshedCollection,
                liveData as MutableLiveData<DownloadStatus>,
                isAutomaticDownload = isAutomaticDownload,
                skipIntegrityCheck = skipIntegrityCheck,
                onConnectionFailure = onConnectionFailure
            )
        }

        cleanUpLiveData()
    }

    /**
     * Iterate the livedata map to see if we can clean up LiveData that have neither references nor observers
     */
    private suspend fun cleanUpLiveData() {
        downloadLiveDataLock.withLock {
            downloadLiveDataMap.entries.removeAll { (_, liveDataWithReferenceCount) ->
                liveDataWithReferenceCount.referenceCount.get() <= 0 && !liveDataWithReferenceCount.liveData.hasObservers()
            }
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
        cleanUpLiveData()
    }

    suspend fun ensureDeletedFiles(collection: DownloadableCollection) {
        withDownloadLiveData(collection) { liveData ->
            collection.setDownloadDate(null)
            (liveData as MutableLiveData<DownloadStatus>).postValue(DownloadStatus.pending)
        }

        when (collection) {
            is Issue -> {
                val filesToDelete: MutableList<FileEntry> =
                    collection.getAllFilesToDelete().toMutableList()
                val filesToRetain =
                    collection.sectionList.fold(mutableListOf<String>()) { acc, section ->
                        // bookmarked articles should remain
                        acc.addAll(
                            section.articleList
                                .filter { it.bookmarked }
                                .map { it.getAllFileNames() }
                                .flatten()
                                .distinct()
                        )
                        // author images are potentially used globally so we retain them for now as they don't eat up much space
                        acc.addAll(
                            section.articleList
                                .map { it.authorList }
                                .flatten()
                                .mapNotNull { it.imageAuthor }
                                .map { it.name }
                        )
                        acc
                    }
                filesToDelete.removeAll { it.name in filesToRetain }

                // do not delete bookmarked files
                articleRepository.apply {
                    getBookmarkedArticleStubListForIssuesAtDate(
                        collection.feedName,
                        collection.date
                    ).forEach {
                        filesToDelete.removeAll(articleStubToArticle(it).getAllFiles())
                    }
                }
                filesToDelete.forEach { storageService.deleteFile(it) }

                collection.setDownloadDate(null)
            }
            is IssueWithPages -> {
                val filesToDelete: MutableList<FileEntry> = collection.getAllFiles().toMutableList()
                val filesToRetain =
                    collection.sectionList.fold(mutableListOf<String>()) { acc, section ->
                        // bookmarked articles should remain
                        acc.addAll(
                            section.articleList
                                .filter { it.bookmarked }
                                .map { it.getAllFileNames() }
                                .flatten()
                                .distinct()
                        )
                        // author images are potentially used globally so we retain them for now as they don't eat up much space
                        acc.addAll(
                            section.articleList
                                .map { it.authorList }
                                .flatten()
                                .mapNotNull { it.imageAuthor }
                                .map { it.name }
                        )
                        acc
                    }
                filesToDelete.removeAll { it.name in filesToRetain }

                // do not delete bookmarked files
                articleRepository.apply {
                    getBookmarkedArticleStubListForIssuesAtDate(
                        collection.feedName,
                        collection.date
                    ).forEach {
                        filesToDelete.removeAll(articleStubToArticle(it).getAllFiles())
                    }
                }
                filesToDelete.forEach { storageService.deleteFile(it) }
                collection.setDownloadDate(null)
            }
            else -> collection.getAllFiles().forEach { storageService.deleteFile(it) }
        }
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

    suspend fun isIssueDownloaded(issueKey: AbstractIssueKey): Boolean =
        withContext(Dispatchers.IO) {
            issueRepository.isDownloaded(issueKey)
        }

    suspend fun determineIssueKey(issuePublication: IssuePublication): IssueKey {
        val regularKey = IssueKey(issuePublication, IssueStatus.regular)
        return if (issueRepository.exists(regularKey) && issueRepository.isDownloaded(regularKey)) {
            regularKey
        } else {
            IssueKey(issuePublication, authHelper.eligibleIssueStatus)
        }
    }

    suspend fun determineIssueKeyWithPages(issuePublication: IssuePublication): IssueKeyWithPages {
        val regularKey = IssueKey(issuePublication, IssueStatus.regular)
        val regularKeyWithPages = IssueKeyWithPages(regularKey)
        return if (issueRepository.exists(regularKey) && issueRepository.isDownloaded(
                regularKeyWithPages
            )
        ) {
            regularKeyWithPages
        } else {
            IssueKeyWithPages(IssueKey(issuePublication, authHelper.eligibleIssueStatus))
        }
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

            downloadLiveDataMap[tag] ?: run {
                val status = when (observableDownload) {
                    is IssueKey ->
                        if (issueRepository.isDownloaded(observableDownload)) DownloadStatus.done
                        else DownloadStatus.pending
                    is IssueKeyWithPages ->
                        if (issueRepository.isDownloaded(observableDownload)) DownloadStatus.done
                        else DownloadStatus.pending
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
        val (liveData, referenceCount) = downloadLiveDataLock.withLock {
            getDownloadLiveData(observableDownload).also {
                it.referenceCount.incrementAndGet()
            }
        }
        try {
            block(liveData)
        } finally {
            referenceCount.decrementAndGet()
        }
    }
}
