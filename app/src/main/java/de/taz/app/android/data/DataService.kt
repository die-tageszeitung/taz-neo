package de.taz.app.android.data

import android.content.Context
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.*
import de.taz.app.android.content.ContentService
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A central service providing data intransparent if from cache or remotely fetched
 */
@Mockable
class DataService(private val applicationContext: Context) {
    companion object : SingletonHolder<DataService, Context>(::DataService)

    private val apiService = ApiService.getInstance(applicationContext)
    private val storageDataStore = StorageDataStore.getInstance(applicationContext)

    private val appInfoRepository = AppInfoRepository.getInstance(applicationContext)
    private val issueRepository = IssueRepository.getInstance(applicationContext)
    private val viewerStateRepository = ViewerStateRepository.getInstance(applicationContext)
    private val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)
    private val pageRepository = PageRepository.getInstance(applicationContext)
    private val feedRepository = FeedRepository.getInstance(applicationContext)
    private val authHelper = AuthHelper.getInstance(applicationContext)
    private val toastHelper = ToastHelper.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)

    private val maxStoredIssueNumberLiveData = storageDataStore.keepIssuesNumber.asLiveData()
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
            maxStoredIssueNumberLiveData.value
        ) { downloaded, max ->
            var downloadedCounter = downloaded
            while (downloadedCounter > max) {
                runBlocking {
                    issueRepository.getEarliestDownloadedIssueStub()?.let {
                        contentService.deleteIssue(it.issueKey)
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
     * This function does not check whether the issue has already been downloaded or not!
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
                    return@withContext it
                }
                // try too read it from database if issue status is not regular -
                // presumably this is so that people with an expired subscription can still access old
                // issues they have saved to database. TODO is this desired?!
                if (authHelper.getEligibleIssueStatus() != IssueStatus.regular) {
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
     * @param retryOnFailure calls getIssue again if unsuccessful
     */
    suspend fun getIssue(
        issuePublication: IssuePublication,
        allowCache: Boolean = true,
        retryOnFailure: Boolean = false,
    ): Issue = withContext(Dispatchers.IO) {
        getIssue(issuePublication, allowCache, retryOnFailure) {}
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
     * @param retryOnFailure calls getIssue again if unsuccessful
     * @param onConnectionFailure callback to handle connection failures
     */
    suspend fun getIssue(
        issuePublication: IssuePublication,
        allowCache: Boolean = true,
        retryOnFailure: Boolean = false,
        onConnectionFailure: suspend () -> Unit = {}
    ): Issue = withContext(Dispatchers.IO) {
        val regularKey = IssueKey(issuePublication, IssueStatus.regular)
        val publicKey = IssueKey(issuePublication, IssueStatus.public)

        if (allowCache) {
            issueRepository.get(regularKey)?.let {
                return@withContext it
            }
            // Only if the eligible status is not regular a public issue is acceptable
            if (authHelper.getEligibleIssueStatus() != IssueStatus.regular) {
                issueRepository.get(publicKey)?.let { return@withContext it }
            }
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
        return@withContext issueRepository.save(issue)
    }

    suspend fun getLastDisplayableOnIssue(issueKey: IssueKey): String? =
        withContext(Dispatchers.IO) {
            issueRepository.getLastDisplayable(issueKey)
        }

    suspend fun saveLastDisplayableOnIssue(issueKey: IssueKey, displayableName: String) =
        withContext(Dispatchers.IO) {
            issueRepository.saveLastDisplayable(issueKey, displayableName)
        }

    suspend fun saveLastPageOnIssue(issueKey: IssueKey, pageName: Int) =
        withContext(Dispatchers.IO) {
            issueRepository.saveLastPagePosition(issueKey, pageName)
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
                // Only if the eligible status is not regular a public issue is acceptable
                if (authHelper.getEligibleIssueStatus() != IssueStatus.regular) {
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

    suspend fun getResourceInfo(
        minResourceVersion: Int,
        retryOnFailure: Boolean = false,
        onConnectionFailure: suspend () -> Unit
    ): ResourceInfo = withContext(Dispatchers.IO) {
        resourceInfoRepository.getNewest()?.let {
            if (it.resourceVersion >= minResourceVersion) {
                return@withContext it
            }
        }

        val resourceInfo = if (retryOnFailure) {
            apiService.retryOnConnectionFailure({
                onConnectionFailure()
            }) {
                apiService.getResourceInfo()
            }
        } else {
            apiService.getResourceInfo()
        }
        resourceInfoRepository.save(resourceInfo)
        return@withContext resourceInfo
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
            IssueKey(issuePublication, authHelper.getEligibleIssueStatus())
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
            IssueKeyWithPages(IssueKey(issuePublication, authHelper.getEligibleIssueStatus()))
        }
    }

    /**
     * Refresh the the Feed with [feedName] and return an [Issue] if a new issue date was detected
     * @param feedName to refresh
     */
    suspend fun refreshFeedAndGetIssueKeyIfNew(feedName: String): IssueKey? =
        withContext(Dispatchers.IO) {
            val cachedFeed = getFeedByName(feedName)
            val refreshedFeed = getFeedByName(feedName, allowCache = false)
            val newsestIssueDate = refreshedFeed?.publicationDates?.getOrNull(0)
            newsestIssueDate?.let {
                if (newsestIssueDate != cachedFeed?.publicationDates?.getOrNull(0)) {
                    determineIssueKey(IssuePublication(feedName, simpleDateFormat.format(it)))
                } else {
                    null
                }
            }
        }

}
