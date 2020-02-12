package de.taz.app.android.api

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.squareup.moshi.JsonEncodingException
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.*
import de.taz.app.android.api.variables.*
import de.taz.app.android.firebase.FirebaseHelper
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
@Mockable
class ApiService private constructor(applicationContext: Context) {

    companion object : SingletonHolder<ApiService, Context>(::ApiService)

    private val log by Log

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var graphQlClient: GraphQlClient = GraphQlClient.getInstance(applicationContext)

    /**
     * function to connect subscriptionId to tazId
     * @param tazId
     * @param idPassword - password for the tazId
     * @param subscriptionId - id of the subscription
     * @param subscriptionPassword - password for the subscriptionId
     * @param surname - surname of the user
     * @param firstname - firstname of the user
     * return [SubscriptionInfo] indicating whether the connection has been successful
     */
    suspend fun subscriptionId2TazId(
        tazId: Int,
        idPassword: String,
        subscriptionId: Int,
        subscriptionPassword: String,
        surname: String? = null,
        firstname: String? = null
    ): SubscriptionInfo? {
        return transformExceptions({
            graphQlClient.query(
                QueryType.SubscriptionId2TazId,
                SubscriptionId2TazIdVariables(
                    tazId,
                    idPassword,
                    subscriptionId,
                    subscriptionPassword,
                    surname,
                    firstname
                )
            )?.subscriptionPoll
        }, "subscriptionId2TazId")
    }

    suspend fun subscriptionPoll(): SubscriptionInfo? {
        val tag = "subscriptionPoll"
        log.debug(tag)
        return transformExceptions({
            graphQlClient.query(
                QueryType.SubscriptionId2TazId,
                SubscriptionPollVariables()
            )?.subscriptionPoll
        }, tag)
    }

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
                QueryType.Authentication,
                AuthenticationVariables(user, password)
            )?.authentificationToken
        }, tag)
    }

    /**
     * function to verify if an subscriptionId password combination is valid
     * @param subscriptionId- the id of the subscription of the user
     * @param password - the password of the user
     * @return [AuthInfo] indicating if combination is valid, elapsed or invalid
     */
    @Throws(ApiServiceException.NoInternetException::class)
    suspend fun checkSubscriptionId(subscriptionId: Int, password: String): AuthInfo? {
        return transformExceptions({
            graphQlClient.query(
                QueryType.CheckSubscriptionId,
                CheckSubscriptionIdVariables(subscriptionId, password)
            )?.checkSubscriptionId
        }, "checkSubscriptionId")
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
            { graphQlClient.query(QueryType.AppInfo)?.product?.let { AppInfo(it) } }, tag
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
            { graphQlClient.query(QueryType.AuthInfo)?.product?.authInfo }, tag
        )
    }


    /**
     * function to get available feeds
     * @return List of [Feed]s
     */
    @Throws(
        ApiServiceException.NoInternetException::class
    )
    suspend fun getFeeds(): List<Feed> {
        val tag = "getFeeds"
        log.debug(tag)
        return transformExceptions({
            graphQlClient.query(QueryType.Feed)?.product?.feedList?.map {
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
                QueryType.LastIssues,
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
    suspend fun getIssuesByDate(
        issueDate: String = simpleDateFormat.format(Date()),
        limit: Int = 10
    ): List<Issue> {
        val tag = "getIssuesByFeedAndDate"
        log.debug("$tag issueDate: $issueDate limit: $limit")
        return transformExceptions({
            val issues = mutableListOf<Issue>()
            graphQlClient.query(
                QueryType.IssueByFeedAndDate,
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
    suspend fun getIssuesByFeedAndDate(
        feedName: String = "taz",
        issueDate: String = simpleDateFormat.format(Date()),
        limit: Int = 2
    ): List<Issue> {
        val tag = "getIssuesByFeedAndDate"
        log.debug("$tag feedName: $feedName issueDate: $issueDate limit: $limit")
        return transformExceptions({
            graphQlClient.query(
                QueryType.IssueByFeedAndDate,
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
            { graphQlClient.query(QueryType.ResourceInfo)?.product?.let { ResourceInfo(it) } },
            tag
        )
    }

    /**
     * function to inform server of started download
     * @param feedName name of the feed the download is started for
     * @param issueDate the date of the issue that is being downloaded
     * @return [String] the id of the download
     */
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

    /**
     * function to inform server of finished download
     * @param id the id of the download received via [notifyServerOfDownloadStart]
     * @param time time in seconds needed for the download
     */
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

    /**
     * function to inform server the notification token
     * @param oldToken the old token of the string if any - will be removed on the server
     */
    @Throws(
        ApiServiceException.NoInternetException::class
    )
    suspend fun sendNotificationInfo(oldToken: String? = null): Boolean? {
        val tag = "sendNotificationInfo"
        log.debug(tag)

        return FirebaseHelper.getInstance().firebaseToken?.let { notificationToken ->
            transformExceptions(
                {
                    graphQlClient.query(
                        QueryType.Notification,
                        NotificationVariables(notificationToken, oldToken = oldToken)
                    )?.notification
                },
                tag
            )
        }
    }

    /**
     * function to request a trial subscription
     * @param tazId the username
     * @param idPassword the password for the username
     * @param surname surname of the requesting person
     * @param firstName firstName of the requesting person
     */
    @Throws(
        ApiServiceException.NoInternetException::class
    )
    suspend fun trialSubscription(
        tazId: String,
        idPassword: String,
        surname: String? = null,
        firstName: String? = null
    ): SubscriptionInfo? {
        val tag = "trialSubscription"
        log.debug("$tag tazId: $tazId")
        return transformExceptions({
            graphQlClient.query(
                QueryType.TrialSubscription,
                TrialSubscriptionVariables(tazId, idPassword, surname, firstName)
            )?.subscriptionPoll
        }, tag)
    }

    @Throws(
        ApiServiceException.NoInternetException::class
    )
    suspend fun sendErrorReport(
            email: String?,
            message: String?,
            lastAction: String?,
            conditions: String?,
            storageType: String?,
            errorProtocol: String?
        ): Boolean? {
        val tag = "sendErrorReport"
        log.debug("$tag email: $email message: $message lastAction: $lastAction conditions: $conditions storageType: $storageType")

        return transformExceptions(
            {
                graphQlClient.query(
                    QueryType.ErrorReport,
                    ErrorReportVariables(email,  message, lastAction, conditions, storageType, errorProtocol)
                )?.errorReport
            },
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