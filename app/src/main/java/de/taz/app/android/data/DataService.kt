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


}
