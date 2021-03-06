package de.taz.app.android.api

import android.content.Context
import androidx.annotation.VisibleForTesting
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.reportAndRethrowExceptions
import java.io.IOException
import kotlin.jvm.Throws

/**
 * Types of queries that are available
 * The names need to match a file with `.graphql` extension in the assets
 */
enum class QueryType {
    AppInfo,
    Authentication,
    CheckSubscriptionId,
    DownloadStart,
    DownloadStop,
    ErrorReport,
    Feed,
    IssueByFeedAndDate,
    Moment,
    LastIssues,
    Notification,
    PasswordReset,
    PriceList,
    ResourceInfo,
    Subscription,
    SubscriptionId2TazId,
    SubscriptionPoll,
    SubscriptionReset,
    TrialSubscription
}

/**
 * service class to get the queries either from cache or by reading the assets file
 */
@Mockable
class QueryService private constructor(applicationContext: Context) {

    companion object : SingletonHolder<QueryService, Context>(::QueryService)

    private val fileHelper = StorageService.getInstance(applicationContext)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val queryCache = mutableMapOf<String, String>()

    @Throws(IOException::class)
    fun get(queryType: QueryType): Query {
        val fileName = queryType.name
        return reportAndRethrowExceptions {
            Query(
            queryCache[fileName] ?: let {
                val queryString = readGraphQlQueryFromAssets(fileName)
                queryCache[fileName] = queryString
                queryString
            })
        }
    }

    @Throws(IOException::class)
    private fun readGraphQlQueryFromAssets(fileName: String): String {
        val fullFilePath =
            "graphql/" + if (fileName.endsWith(".graphql")) fileName else "$fileName.graphql"
        return fileHelper.readFileFromAssets(fullFilePath).trimIndent()
    }
}