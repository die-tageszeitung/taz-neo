package de.taz.app.android.api

import android.content.Context
import androidx.annotation.VisibleForTesting
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.reportAndRethrowExceptions
import java.io.IOException

/**
 * Types of queries that are available
 * The names need to match a file with `.graphql` extension in the assets
 */
enum class QueryType {
    App,
    AppInfo,
    Authentication,
    CheckSubscriptionId,
    DeleteCustomerData,
    DownloadStart,
    DownloadStop,
    ErrorReport,
    Feed,
    GetCustomerData,
    IssueByFeedAndDate,
    Moment,
    LastIssues,
    Notification,
    PasswordReset,
    ResourceInfo,
    SaveCustomerData,
    SubscriptionId2TazId,
    SubscriptionPoll,
    SubscriptionReset,
    TrialSubscription,
    Search,
    Cancellation,
    SubscriptionFormData,
    CustomerInfo,
}

/**
 * service class to get the queries either from cache or by reading the assets file
 */
class QueryService private constructor(applicationContext: Context) {

    companion object : SingletonHolder<QueryService, Context>(::QueryService)

    private val fileHelper = StorageService.getInstance(applicationContext)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val queryCache = mutableMapOf<String, String>()

    @Throws(IOException::class)
    suspend fun get(queryType: QueryType): Query {
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