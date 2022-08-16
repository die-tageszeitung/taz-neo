package de.taz.app.android.data

import android.content.Context
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.*
import de.taz.app.android.content.ContentService
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.util.SingletonHolder

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

    private val feedRepository = FeedRepository.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)

    suspend fun getLastDisplayableOnIssue(issueKey: IssueKey): String? =
        issueRepository.getLastDisplayable(issueKey)

    suspend fun saveLastDisplayableOnIssue(issueKey: IssueKey, displayableName: String) =
        issueRepository.saveLastDisplayable(issueKey, displayableName)

    suspend fun saveLastPageOnIssue(issueKey: IssueKey, pageName: Int) =
        issueRepository.saveLastPagePosition(issueKey, pageName)

    suspend fun getFeedByName(
        name: String,
        allowCache: Boolean = true,
        retryOnFailure: Boolean = false
    ): Feed? {
        if (allowCache) {
            feedRepository.get(name)?.let {
                return it
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
        return feed
    }

    /**
     * Refresh the the Feed with [feedName] and return an [Issue] if a new issue date was detected
     * @param feedName to refresh
     */
    suspend fun refreshFeedAndGetIssueKeyIfNew(
        feedName: String
    ): IssueKey? {
            val cachedFeed = getFeedByName(feedName)
            val refreshedFeed = getFeedByName(feedName, allowCache = false)

            val newestIssueDate = refreshedFeed?.publicationDates?.getOrNull(0)
            val cachedIssueDate = cachedFeed?.publicationDates?.getOrNull(0)

            return if (newestIssueDate != null && newestIssueDate != cachedIssueDate) {
                (contentService.downloadMetadata(
                    download = IssuePublication(feedName, simpleDateFormat.format(newestIssueDate)),
                    maxRetries = 3
                ) as Issue).issueKey
            } else {
                null
            }
        }

}
