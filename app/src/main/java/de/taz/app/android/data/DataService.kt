package de.taz.app.android.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.download.DownloadService
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap


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
    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val sectionRepository = SectionRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)
    private val downloadService = DownloadService.getInstance(applicationContext)
    private val feedRepository = FeedRepository.getInstance(applicationContext)

    private val downloadLiveDataMap: ConcurrentHashMap<String, LiveData<DownloadStatus>> =
        ConcurrentHashMap()

    suspend fun getIssueStub(issueKey: IssueKey, allowCache: Boolean = true): IssueStub =
        withContext(Dispatchers.IO) {
            if (allowCache) {
                issueRepository.getStub(issueKey)?.let { return@withContext it } ?: run {
                    log.info("Cache miss on $issueKey")
                }
            }
            IssueStub(getIssue(issueKey, allowCache))
        }

    suspend fun getLatestIssue(
        feedNames: List<String>,
        status: IssueStatus,
        allowCache: Boolean = false
    ): Issue? = withContext(Dispatchers.IO) {
        if (allowCache) {
            issueRepository.getLatestIssueByFeedAndStatus(feedNames, status)?.let {
                return@withContext it
            }
        }
        val issue = apiService.getLastIssuesByFeed(feedNames, 1)
        issueRepository.save(issue)
        issue.first()
    }

    suspend fun getIssue(
        issueKey: IssueKey,
        allowCache: Boolean = true,
        skipSaveCache: Boolean = false
    ): Issue = withContext(Dispatchers.IO) {
        if (allowCache) {
            issueRepository.get(issueKey)?.let { return@withContext it } ?: run {
                log.info("Cache miss on $issueKey")
            }
        }
        val issue = apiService.getIssueByKey(issueKey)
        if (issue.status == issueKey.status) {
            // cache issue after download
            if (!skipSaveCache) {
                issueRepository.save(issue)
            }
            return@withContext issue
        } else {
            throw DataException.InconsistentRequestException("The requested issue ($issueKey) has status \"${issueKey.status}\" but server returned another one, probably because of out-of-sync auth status")
        }
    }


    suspend fun getIssueStubs(
        fromDate: Date = Date(),
        limit: Int = 1,
        allowCache: Boolean = true
    ): List<IssueStub> = withContext(Dispatchers.IO) {
        if (allowCache) {
            val issues = issueRepository.getLatestIssueStubsByDate(
                simpleDateFormat.format(fromDate), limit
            )
            if (issues.size == limit) {
                return@withContext issues
            }
        }
        val feedNames = feedRepository.getAll().map { it.name }
        val issues = feedNames.map { feedName ->
            apiService.getIssuesByFeedAndDate(feedName, fromDate, limit)
        }.flatten()
        if (allowCache) {
            issueRepository.saveIfDoNotExist(issues)
        } else {
            issueRepository.save(issues)
        }
        issues.map(::IssueStub)
    }

    suspend fun getIssueStubsByFeed(
        fromDate: Date = Date(),
        feedNames: List<String>,
        limit: Int = 1,
        allowCache: Boolean = true
    ): List<IssueStub> = withContext(Dispatchers.IO) {
        if (allowCache) {
            val issues = issueRepository.getLatestIssueStubsByFeedAndDate(
                simpleDateFormat.format(fromDate), feedNames, limit
            )
            if (issues.size == limit) {
                return@withContext issues
            }
        }
        val issues = feedNames.map { feedName ->
            apiService.getIssuesByFeedAndDate(feedName, fromDate, limit)
        }.flatten()
        issueRepository.save(issues)
        issues.map(::IssueStub)
    }

    suspend fun getIssueStubsByFeed(
        fromDate: Date = Date(),
        feedNames: List<String>,
        status: IssueStatus,
        limit: Int = 1,
        allowCache: Boolean = true
    ): List<IssueStub> = withContext(Dispatchers.IO) {
        if (allowCache) {
            val issues = issueRepository.getIssuesFromDate(fromDate, feedNames, status, limit)
            if (issues.isNotEmpty()) {
                return@withContext issues
            }
        }
        val issues = feedNames.map { feedName ->
            apiService.getIssuesByFeedAndDate(feedName, fromDate, limit)
                .filter { it.status == status }
        }.flatten()
        issueRepository.save(issues)
        issues.map(::IssueStub)
    }

    suspend fun getAppInfo(allowCache: Boolean = true, retryOnFailure: Boolean = false): AppInfo = withContext(Dispatchers.IO) {
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

    suspend fun getMoment(issue: Issue, allowCache: Boolean = true): Moment =
        withContext(Dispatchers.IO) {
            return@withContext momentRepository.get(issue)
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

    suspend fun getResourceInfoStub(allowCache: Boolean = true): ResourceInfoStub =
        withContext(Dispatchers.IO) {
            if (allowCache) {
                resourceInfoRepository.getNewestStub()?.let {
                    return@withContext it
                }
            }
            val resourceInfo = apiService.getResourceInfo()
            resourceInfoRepository.save(resourceInfo)
            ResourceInfoStub(resourceInfo)
        }

    suspend fun ensureDownloaded(
        collection: DownloadableCollection,
        isAutomaticDownload: Boolean = false
    ) {
        downloadService.ensureCollectionDownloaded(
            collection,
            getDownloadLiveData(collection) as MutableLiveData<DownloadStatus>,
            isAutomaticDownload = isAutomaticDownload
        )
        cleanUpLiveData()
    }

    private fun cleanUpLiveData() {
        downloadLiveDataMap.forEach { (tag, liveData) ->
            if (!liveData.hasObservers()) {
                downloadLiveDataMap.remove(tag)
            }
        }
    }

    suspend fun ensureDownloaded(fileEntry: FileEntry, baseUrl: String) {
        if (!fileHelper.ensureFileIntegrity(fileEntry.path, fileEntry.sha256)) {
            downloadService.downloadAndSaveFile(fileEntry, baseUrl)
        }
    }

    suspend fun ensureDeleted(collection: DownloadableCollection) {
        val statusLiveData = getDownloadLiveData(collection) as MutableLiveData<DownloadStatus>
        collection.setDownloadDate(null)
        statusLiveData.postValue(DownloadStatus.pending)
        collection.getAllFiles().forEach { fileHelper.deleteFile(it) }
        cleanUpLiveData()
    }

    suspend fun sendNotificationInfo(token: String, oldToken: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            apiService.sendNotificationInfo(token, oldToken)
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

    fun getDownloadLiveData(downloadableCollection: DownloadableCollection): LiveData<DownloadStatus> {
        val tag = downloadableCollection.getDownloadTag()
        val status = downloadableCollection.dateDownload?.let { DownloadStatus.done }
            ?: DownloadStatus.pending
        return downloadLiveDataMap[tag] ?: run {
            val downloadLiveData = MutableLiveData(status)
            downloadLiveDataMap[tag] = downloadLiveData as LiveData<DownloadStatus>
            downloadLiveData
        }
    }
}
