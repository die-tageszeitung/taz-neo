package de.taz.app.android.api

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.squareup.moshi.JsonEncodingException
import de.taz.app.android.api.models.*
import de.taz.app.android.api.variables.*
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import io.sentry.Sentry
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service class to get Models from GraphQl
 * The DTO objects returned by [GraphQlClient] will be transformed to models here
 */
open class ApiService private constructor(applicationContext: Context) {

    companion object : SingletonHolder<ApiService, Context>(::ApiService)

    private val log by Log

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var graphQlClient: GraphQlClient = GraphQlClient.getInstance(applicationContext)

    /**
     * function to authenticate with the backend
     * @param user - username or id of the user
     * @param password - the password of the user
     * @return [AuthTokenInfo] indicating if authentication has been successful and with token if successful
     */
    @Throws(ApiServiceException.NoInternetException::class)
    suspend fun authenticate(user: String, password: String): AuthTokenInfo? {
        val tag = "authenticate"
        log.debug("$tag username: $user")
        return transformExceptions({
            graphQlClient.query(
                QueryType.AuthenticationQuery,
                AuthenticationVariables(user, password)
            )?.authentificationToken
        }, tag)
    }

    /**
     * function to get the app info
     * @return [AppInfo] with [AppInfo.appName] and [AppInfo.appType]
     */
    @Throws(
        ApiServiceException.NoInternetException::class
    )
    suspend fun getAppInfo(): AppInfo? {
        val tag = "getAppInfo"
        log.debug(tag)
        return transformExceptions(
            { graphQlClient.query(QueryType.AppInfoQuery)?.product?.let { AppInfo(it) } }, tag
        )
    }

    /**
     * function to get current authentication information
     * @return [AuthInfo] indicating if authenticated and if not why not
     */
    @Throws(
        ApiServiceException.NoInternetException::class
    )
    suspend fun getAuthInfo(): AuthInfo? {
        val tag = "getAuthInfo"
        log.debug(tag)
        return transformExceptions(
            { graphQlClient.query(QueryType.AuthInfoQuery)?.product?.authInfo }, tag
        )
    }


    /**
     * function to get available feeds
     * @return List of [Feed]s
     */
    @Throws(
        ApiServiceException.NoInternetException::class
    )
    open suspend fun getFeeds(): List<Feed> {
        val tag = "getFeeds"
        log.debug(tag)
        return transformExceptions({
            graphQlClient.query(QueryType.FeedQuery)?.product?.feedList?.map {
                Feed(it)
            }
        }, tag) ?: emptyList()
    }

    /**
     * function to get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @return [Issue] of the feed at given date
     */
    @Throws(
        ApiServiceException.NoInternetException::class
    )
    suspend fun getIssueByFeedAndDate(
        feedName: String = "taz",
        issueDate: String = simpleDateFormat.format(Date())
    ): Issue? {
        val tag = "getIssueByFeedAndDate"
        log.debug("$tag feedName: $feedName issueDate: $issueDate")
        return transformExceptions({
            getIssuesByFeedAndDate(feedName, issueDate, 1).first()
        }, tag)
    }

    @Throws(ApiServiceException.NoInternetException::class)
    suspend fun getLastIssues(limit: Int = 10): List<Issue> {
        val tag = "getLastIssues"
        log.debug("$tag limit: $limit")
        return transformExceptions({
            val issues = mutableListOf<Issue>()
            graphQlClient.query(
                QueryType.LastIssuesQuery,
                IssueVariables(limit = limit)
            )?.product?.feedList?.forEach { feed ->
                issues.addAll(feed.issueList!!.map { Issue(feed.name!!, it) })
            }
            issues
        }, tag) ?: emptyList()
    }

    /**
     * function to get [Issue]s by date
     * @param issueDate - the date of the issue last issue
     * @param limit - how many issues will be returned
     * @return [Issue] of the feed at given date
     */
    @Throws(
        ApiServiceException.NoInternetException::class
    )
    open suspend fun getIssuesByDate(
        issueDate: String = simpleDateFormat.format(Date()),
        limit: Int = 10
    ): List<Issue> {
        val tag = "getIssuesByFeedAndDate"
        log.debug("$tag issueDate: $issueDate limit: $limit")
        return transformExceptions({
            val issues = mutableListOf<Issue>()
            graphQlClient.query(
                QueryType.IssueByFeedAndDateQuery,
                IssueVariables(issueDate = issueDate, limit = limit)
            )?.product?.feedList?.forEach { feed ->
                issues.addAll(feed.issueList!!.map { Issue(feed.name!!, it) })
            }
            issues
        }, tag) ?: emptyList()
    }

    /**
     * function to get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @param limit - how many issues will be returned
     * @return [Issue] of the feed at given date
     */
    @Throws(
        ApiServiceException.NoInternetException::class
    )
    open suspend fun getIssuesByFeedAndDate(
        feedName: String = "taz",
        issueDate: String = simpleDateFormat.format(Date()),
        limit: Int = 2
    ): List<Issue> {
        val tag = "getIssuesByFeedAndDate"
        log.debug("$tag feedName: $feedName issueDate: $issueDate limit: $limit")
        return transformExceptions({
            graphQlClient.query(
                QueryType.IssueByFeedAndDateQuery,
                IssueVariables(feedName, issueDate, limit)
            )?.product?.feedList?.first()?.issueList?.map { Issue(feedName, it) }
        }, tag) ?: emptyList()
    }

    /**
     * function to get information about the current resources
     * @return [ResourceInfo] with the current [ResourceInfo.resourceVersion] and the information needed to download it
     */
    @Throws(
        ApiServiceException.NoInternetException::class
    )
    suspend fun getResourceInfo(): ResourceInfo? {
        val tag = "getResourceInfo"
        log.debug(tag)
        return transformExceptions(
            { graphQlClient.query(QueryType.ResourceInfoQuery)?.product?.let { ResourceInfo(it) } },
            tag
        )
    }

    @Throws(
        ApiServiceException.NoInternetException::class
    )
    suspend fun notifyServerOfDownloadStart(
        feedName: String,
        issueDate: String
    ): String? {
        return transformExceptions(
            {
                graphQlClient.query(
                    QueryType.DownloadStart,
                    DownloadStartVariables(
                        feedName,
                        issueDate
                    )
                )?.downloadStart?.let { id ->
                    log.debug("Notified server that download started. ID: $id")
                    id
                }
            },
            "notifyServerOfDownloadStart"
        )
    }

    @Throws(ApiServiceException.NoInternetException::class)
    suspend fun notifyServerOfDownloadStop(
        id: String,
        time: Float
    ): Boolean? {
        val tag = "notifyServerOfDownloadStop"
        log.debug("$tag downloadId: $id time: $time")
        return transformExceptions(
            {
                log.debug("Notifying server that download complete. ID: $id")
                graphQlClient.query(
                    QueryType.DownloadStop,
                    DownloadStopVariables(id, time)
                )?.downloadStop
            },
            tag
        )
    }

    @Throws(
        ApiServiceException.NoInternetException::class
    )
    suspend fun sendNotificationInfo(): Boolean? {
        val tag = "sendNotificationInfo"
        log.debug(tag)

        return transformExceptions(
            { graphQlClient.query(QueryType.Notification, NotificationVariables())?.notification },
            tag
        )
    }

    @Throws(
        ApiServiceException.NoInternetException::class
    )
    private suspend fun <T> transformExceptions(block: suspend () -> T, tag: String): T? {
        try {
            return block()
        } catch (uhe: UnknownHostException) {
            log.debug("UnknownHostException ${uhe.localizedMessage}")
            throw ApiServiceException.NoInternetException()
        } catch (ce: ConnectException) {
            log.debug("ConnectException ${ce.localizedMessage}")
            throw ApiServiceException.NoInternetException()
        } catch (ste: SocketTimeoutException) {
            log.debug("SocketTimeoutException ${ste.localizedMessage}")
            throw ApiServiceException.NoInternetException()
        } catch (jee: JsonEncodingException) {
            // inform sentry of malformed JSON response
            Sentry.capture(ApiServiceException.WrongDataException())
        } catch (npe: NullPointerException) {
            // inform sentry of missing data in response
            Sentry.capture(ApiServiceException.InsufficientDataException(tag))
        }
        return null
    }

    object ApiServiceException {
        class InsufficientDataException(function: String) :
            Exception("ApiService.$function failed.")

        class NoInternetException : Exception("no internet connection")
        class WrongDataException : Exception("data could not be parsed")
    }

}