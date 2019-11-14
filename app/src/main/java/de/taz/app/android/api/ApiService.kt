package de.taz.app.android.api

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.squareup.moshi.JsonEncodingException
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.api.models.*
import de.taz.app.android.api.variables.AuthenticationVariables
import de.taz.app.android.api.variables.DownloadStartVariables
import de.taz.app.android.api.variables.DownloadStopVariables
import de.taz.app.android.api.variables.IssueVariables
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
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
    @Throws(
        ApiServiceException.InsufficientDataException::class,
        ApiServiceException.NoInternetException::class,
        ApiServiceException.WrongDataException::class
    )
    suspend fun authenticate(user: String, password: String): AuthTokenInfo {
        return catchExceptions({
            graphQlClient.query(
                QueryType.AuthenticationQuery,
                AuthenticationVariables(user, password)
            ).authentificationToken!!
        }, "authenticate")
    }

    /**
     * function to get the app info
     * @return [AppInfo] with [AppInfo.appName] and [AppInfo.appType]
     */
    @Throws(
        ApiServiceException.InsufficientDataException::class,
        ApiServiceException.NoInternetException::class,
        ApiServiceException.WrongDataException::class
    )
    suspend fun getAppInfo(): AppInfo {
        return catchExceptions(
            { AppInfo(graphQlClient.query(QueryType.AppInfoQuery).product!!) },
            "getAppInfo"
        )
    }

    /**
     * function to get current authentication information
     * @return [AuthInfo] indicating if authenticated and if not why not
     */
    @Throws(
        ApiServiceException.InsufficientDataException::class,
        ApiServiceException.NoInternetException::class,
        ApiServiceException.WrongDataException::class
    )
    suspend fun getAuthInfo(): AuthInfo {
        return catchExceptions(
            { graphQlClient.query(QueryType.AuthInfoQuery).product!!.authInfo!! },
            "getAuthInfo"
        )
    }


    /**
     * function to get available feeds
     * @return List of [Feed]s
     */
    @Throws(
        ApiServiceException.InsufficientDataException::class,
        ApiServiceException.NoInternetException::class,
        ApiServiceException.WrongDataException::class
    )
    open suspend fun getFeeds(): List<Feed> {
        return catchExceptions(
            {
                graphQlClient.query(QueryType.FeedQuery).product!!.feedList?.map {
                    Feed(it)
                } ?: emptyList()
            },
            "getFeeds"
        )
    }

    /**
     * function to get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @return [Issue] of the feed at given date
     */
    @Throws(
        ApiServiceException.InsufficientDataException::class,
        ApiServiceException.NoInternetException::class,
        ApiServiceException.WrongDataException::class
    )
    suspend fun getIssueByFeedAndDate(
        feedName: String = "taz",
        issueDate: String = simpleDateFormat.format(Date())
    ): Issue {
        return catchExceptions({
            getIssuesByFeedAndDate(feedName, issueDate, 1).first()
        }, "getIssueByFeedAndDate")
    }

    /**
     * function to get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @param limit - how many issues will be returned
     * @return [Issue] of the feed at given date
     */
    @Throws(
        ApiServiceException.InsufficientDataException::class,
        ApiServiceException.NoInternetException::class,
        ApiServiceException.WrongDataException::class
    )
    open suspend fun getIssuesByDate(
        issueDate: String = simpleDateFormat.format(Date()),
        limit: Int = 10
    ): List<Issue> {
        return catchExceptions({
            val issues = mutableListOf<Issue>()
            graphQlClient.query(
                QueryType.IssueByFeedAndDateQuery,
                IssueVariables(issueDate = issueDate, limit = limit)
            ).product!!.feedList!!.forEach { feed ->
                issues.addAll(feed.issueList!!.map { Issue(feed.name!!, it) })
            }
            issues
        }, "getIssuesByFeedAndDate")
    }

    /**
     * function to get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @param limit - how many issues will be returned
     * @return [Issue] of the feed at given date
     */
    @Throws(
        ApiServiceException.InsufficientDataException::class,
        ApiServiceException.NoInternetException::class,
        ApiServiceException.WrongDataException::class
    )
    open suspend fun getIssuesByFeedAndDate(
        feedName: String = "taz",
        issueDate: String = simpleDateFormat.format(Date()),
        limit: Int = 2
    ): List<Issue> {
        return catchExceptions({
            graphQlClient.query(
                QueryType.IssueByFeedAndDateQuery,
                IssueVariables(feedName, issueDate, limit)
            ).product!!.feedList!!.first().issueList!!.map { Issue(feedName, it) }
        }, "getIssuesByFeedAndDate")
    }

    /**
     * function to get information about the current resources
     * @return [ResourceInfo] with the current [ResourceInfo.resourceVersion] and the information needed to download it
     */
    @Throws(
        ApiServiceException.InsufficientDataException::class,
        ApiServiceException.NoInternetException::class,
        ApiServiceException.WrongDataException::class
    )
    suspend fun getResourceInfo(): ResourceInfo {
        return catchExceptions(
            { ResourceInfo(graphQlClient.query(QueryType.ResourceInfoQuery).product!!) },
            "getResourceInfo"
        )
    }

    @Throws(
        ApiServiceException.InsufficientDataException::class,
        ApiServiceException.NoInternetException::class,
        ApiServiceException.WrongDataException::class
    )
    suspend fun notifyServerOfDownloadStart(
        feedName: String,
        issueDate: String,
        deviceName: String = "TODO",
        deviceVersion: String = "TODO",
        appVersion: String = "TODO",
        isPush: Boolean = false, // TODO
        installationId: UUID = UUID.randomUUID(), // TODO
        deviceFormat: DeviceFormat = DeviceFormat.mobile,
        deviceType: DeviceType = DeviceType.android
    ): String {
        return catchExceptions(
            {
                val data = graphQlClient.query(
                    QueryType.DownloadStart,
                    DownloadStartVariables(
                        feedName,
                        issueDate,
                        deviceName,
                        deviceVersion,
                        appVersion,
                        isPush,
                        installationId.toString(),
                        deviceFormat,
                        deviceType
                    )
                )
                val id = data.downloadStart!!
                log.debug("Notified server that download started. ID: $id")
                id
            },
            "notifyServerOfDownloadStart"
        )
    }

    @Throws(
        ApiServiceException.InsufficientDataException::class,
        ApiServiceException.NoInternetException::class,
        ApiServiceException.WrongDataException::class
    )
    suspend fun notifyServerOfDownloadStop(
        id: String,
        time: Float
    ): Boolean {
        return catchExceptions(
            {
                log.debug("Notifying server that download complete. ID: $id")
                graphQlClient.query(
                    QueryType.DownloadStop,
                    DownloadStopVariables(id, time)
                ).downloadStop!!
            },
            "notifyServerOfDownloadStop"
        )
    }

    @Throws(
        ApiServiceException.InsufficientDataException::class,
        ApiServiceException.NoInternetException::class,
        ApiServiceException.WrongDataException::class
    )
    private suspend fun <T> catchExceptions(block: suspend () -> T, tag: String): T {
        return try {
            block()
        } catch (npe: NullPointerException) {
            throw ApiServiceException.InsufficientDataException(tag)
        } catch (uhe: UnknownHostException) {
            throw ApiServiceException.NoInternetException()
        } catch (ce: ConnectException) {
            throw ApiServiceException.NoInternetException()
        } catch (ste: SocketTimeoutException) {
            throw ApiServiceException.NoInternetException()
        } catch (jee: JsonEncodingException) {
            throw ApiServiceException.WrongDataException()
        }
    }


    object ApiServiceException {
        class InsufficientDataException(function: String) :
            Exception("ApiService.$function failed.")

        class NoInternetException : Exception("no internet connection")
        class WrongDataException : Exception("data could not be parsed")
    }

}