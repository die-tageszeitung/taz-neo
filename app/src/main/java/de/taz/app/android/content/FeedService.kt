package de.taz.app.android.content

import android.content.Context
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.Issue
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Mockable
class FeedService(applicationContext: Context) {
    companion object : SingletonHolder<FeedService, Context>(::FeedService)

    private val apiService = ApiService.getInstance(applicationContext)
    private val feedRepository = FeedRepository.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)


    @Deprecated("Caller should not care about retries")
    suspend fun refreshFeed(name: String, retryOnFailure: Boolean = false): Feed? =
        withContext(Dispatchers.IO) {
            apiService.getFeedByName(name)?.apply {
                feedRepository.save(this)
            }
        }

    @Deprecated("Caller should not care about retries")
    fun getFeedFlowByName(name: String, retryOnFailure: Boolean): Flow<Feed?> {
        return feedRepository.getFlow(name)
            .distinctUntilChanged { old, new -> Feed.equalsShallow(old, new) }
            .map {
                // Refresh (download) the latest feed if it is currently missing (feed being null)
                // and emit the download result
                it ?: refreshFeed(name, retryOnFailure)
            }
    }

    fun getFeedFlowByName(name: String): Flow<Feed?> =
        getFeedFlowByName(name, retryOnFailure = false)

    /**
     * Refresh the the Feed with [name] and return an [IssueKey] if a new issue date was detected.
     * Returns null if the feed was already up to date and did not need a refresh.
     */
    suspend fun refreshFeedAndGetIssueKeyIfNew(name: String): IssueKey? {
        val cachedFeed = feedRepository.get(name)
        val refreshedFeed = refreshFeed(name)

        val newestIssueDate = refreshedFeed?.publicationDates?.getOrNull(0)?.date
        val cachedIssueDate = cachedFeed?.publicationDates?.getOrNull(0)?.date

        return if (newestIssueDate != null && newestIssueDate != cachedIssueDate) {
            (contentService.downloadMetadata(
                download = IssuePublication(name, simpleDateFormat.format(newestIssueDate)),
                maxRetries = 3
            ) as Issue).issueKey
        } else {
            null
        }
    }
}