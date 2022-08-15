package de.taz.app.android.data

import android.content.Context
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.*
import de.taz.app.android.content.ContentService
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.ui.splash.MAX_RETRIES_ON_STARTUP
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.*

/**
 * A central service providing data intransparent if from cache or remotely fetched
 */
@Mockable
@Deprecated(
    "This class should not be used anymore - either use the respective repositories" +
            "or the CacheOperations"
)
class DataService(applicationContext: Context) {
    companion object : SingletonHolder<DataService, Context>(::DataService)

    private val apiService = ApiService.getInstance(applicationContext)

    private val issueRepository = IssueRepository.getInstance(applicationContext)
    private val viewerStateRepository = ViewerStateRepository.getInstance(applicationContext)

    private val feedRepository = FeedRepository.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)

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

    /**
     * Refresh the the Feed with [feedName] and return an [Issue] if a new issue date was detected
     * @param feedName to refresh
     */
    suspend fun refreshFeedAndGetIssueKeyIfNew(
        feedName: String
    ): IssueKey? =
        withContext(Dispatchers.IO) {
            val cachedFeed = getFeedByName(feedName)
            val refreshedFeed = getFeedByName(feedName, allowCache = false)

            val newestIssueDate = refreshedFeed?.publicationDates?.getOrNull(0)
            val cachedIssueDate = cachedFeed?.publicationDates?.getOrNull(0)

            if (newestIssueDate != null && newestIssueDate != cachedIssueDate) {
                (contentService.downloadMetadata(
                    download = IssuePublication(feedName, simpleDateFormat.format(newestIssueDate)),
                    maxRetries = 3
                ) as Issue).issueKey
            } else {
                null
            }
        }

}
