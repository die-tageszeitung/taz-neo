package de.taz.app.android.api

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Transformations
import com.squareup.moshi.JsonEncodingException
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.dto.DataDto
import de.taz.app.android.api.dto.ProductDto
import de.taz.app.android.api.models.*
import de.taz.app.android.api.variables.*
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ServerConnectionHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.SingletonHolder
import io.sentry.Sentry
import kotlinx.coroutines.*
import java.io.EOFException
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.SSLException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Service class to get Models from GraphQl
 * The DTO objects returned by [GraphQlClient] will be transformed to models here
 */
@Mockable
class ApiService private constructor(applicationContext: Context) {

    companion object : SingletonHolder<ApiService, Context>(::ApiService)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var graphQlClient: GraphQlClient = GraphQlClient.getInstance(applicationContext)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var authHelper: AuthHelper = AuthHelper.getInstance(applicationContext)

    private val serverConnectionHelper = ServerConnectionHelper.getInstance(applicationContext)
    private val toastHelper = ToastHelper.getInstance(applicationContext)
    private val waitInternetList = mutableListOf<Continuation<Unit>>()

    private val firebaseHelper = FirebaseHelper.getInstance(applicationContext)

    init {
        Transformations.distinctUntilChanged(serverConnectionHelper.isServerReachableLiveData)
            .observeForever { canReach ->
                if (canReach) {
                    waitInternetList.forEach {
                        try {
                            log.debug("ApiCall resuming")
                            it.resume(Unit)
                        } catch (ise: IllegalStateException) {
                            // already resumed
                        }
                    }
                }
            }
    }

    /**
     * function to connect subscriptionId to tazId
     * @param tazId
     * @param idPassword - password for the tazId
     * @param subscriptionId - id of the subscription
     * @param subscriptionPassword - password for the subscriptionId
     * @param surname - surname of the user
     * @param firstName - firstname of the user
     * return [SubscriptionInfo] indicating whether the connection has been successful
     */
    @Throws(ApiServiceException.NoInternetException::class)
    suspend fun subscriptionId2TazId(
        tazId: String,
        idPassword: String,
        subscriptionId: Int,
        subscriptionPassword: String,
        surname: String? = null,
        firstName: String? = null
    ): SubscriptionInfo? {
        val tag = "subscriptionId2TazId"
        return transformExceptions({
            graphQlClient.query(
                QueryType.SubscriptionId2TazId,
                SubscriptionId2TazIdVariables(
                    tazId,
                    idPassword,
                    subscriptionId,
                    subscriptionPassword,
                    surname,
                    firstName
                )
            )?.subscriptionId2tazId
        }, tag)
    }

    @Throws(ApiServiceException.NoInternetException::class)
    suspend fun subscriptionPoll(): SubscriptionInfo? {
        val tag = "subscriptionPoll"
        log.debug(tag)
        return transformExceptions({
            graphQlClient.query(
                QueryType.SubscriptionPoll,
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
    suspend fun checkSubscriptionId(
        subscriptionId: Int,
        password: String
    ): AuthInfo? {
        val tag = "checkSubscriptionId"
        return transformExceptions(
            {
                graphQlClient.query(
                    QueryType.CheckSubscriptionId,
                    CheckSubscriptionIdVariables(subscriptionId, password)
                )?.checkSubscriptionId
            }, tag
        )
    }


    /**
     * function to get the app info
     * @return [AppInfo] with [AppInfo.appName] and [AppInfo.appType]
     */
    suspend fun getAppInfoAsync(): Deferred<AppInfo?> = CoroutineScope(Dispatchers.IO).async {
        val tag = "getAppInfo"
        getDataDto(tag, QueryType.AppInfo).product?.let { AppInfo(it) }
    }

    /**
     * function to asynchronously get available feeds
     * @return List of [Feed]s
     */
    @Throws(ApiServiceException.NoInternetException::class)
    suspend fun getFeedsAsync(): Deferred<List<Feed>> = CoroutineScope(Dispatchers.IO).async {
        val tag = "getFeedsAsync"
        log.debug(tag)
        getDataDto(tag, QueryType.Feed).product?.feedList?.map { Feed(it) } ?: emptyList()
    }

    /**
     * function to get available feeds
     * @return List of [Feed]s
     */
    @Throws(ApiServiceException.NoInternetException::class)
    suspend fun getFeeds(): List<Feed> {
        val tag = "getFeeds"
        log.debug(tag)
        return transformExceptions({
            graphQlClient.query(QueryType.Feed)?.product?.feedList?.map { Feed(it) }
        }, tag) ?: emptyList()
    }

    /**
     * function to asynchronously get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @return [Issue] of the feed at given date
     */
    suspend fun getIssueByFeedAndDateAsync(
        feedName: String = "taz",
        issueDate: String = simpleDateFormat.format(Date())
    ): Deferred<Issue?> = CoroutineScope(Dispatchers.IO).async {
        val tag = "getIssueByFeedAndDate"
        log.debug("$tag feedName: $feedName issueDate: $issueDate")
        val issueList = getIssuesByFeedAndDateAsync(feedName, issueDate, 1).await()
        issueList.firstOrNull()
    }

    /**
     * function to get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @return [Issue] of the feed at given date
     */
    @Throws(ApiServiceException.NoInternetException::class)
    suspend fun getIssueByFeedAndDate(
        feedName: String = "taz",
        issueDate: String = simpleDateFormat.format(Date())
    ): Issue? {
        val tag = "getIssueByFeedAndDate"
        log.debug("$tag feedName: $feedName issueDate: $issueDate")
        val issueList = getIssuesByFeedAndDate(feedName, issueDate, 1)
        return issueList.firstOrNull()
    }

    /**
     * function to get the last [Issue]s
     * @param limit - number of issues to get
     * @return [List]<[Issue]>
     */
    @Throws(ApiServiceException.NoInternetException::class)
    suspend fun getLastIssues(limit: Int = 10): List<Issue> {
        val tag = "getLastIssues"
        val issues = mutableListOf<Issue>()
        transformExceptions({
            graphQlClient.query(
                QueryType.LastIssues,
                IssueVariables(limit = limit)
            )?.product?.feedList?.forEach { feed ->
                issues.addAll((feed.issueList ?: emptyList()).map { Issue(feed.name!!, it) })
            }
        }, tag)
        return issues
    }

    /**
     * function to asynchronously get the last [Issue]s
     * @param limit - number of issues to get
     * @return [Deferred]<[List]<[Issue]>>
     */
    suspend fun getLastIssuesAsync(limit: Int = 10): Deferred<List<Issue>> =
        CoroutineScope(Dispatchers.IO).async {
            val tag = "getLastIssues"
            log.debug("$tag limit: $limit")
            val issues = mutableListOf<Issue>()
            getDataDto(
                tag,
                QueryType.LastIssues,
                IssueVariables(limit = limit)
            ).product?.feedList?.forEach { feed ->
                issues.addAll(feed.issueList!!.map { Issue(feed.name!!, it) })
            }
            issues
        }

    /**
     * function to get [Issue]s by date
     * @param issueDate - the date of the issue last issue
     * @param limit - how many issues will be returned
     * @return [Issue] of the feed at given date
     */
    suspend fun getIssuesByDateAsync(
        issueDate: String = simpleDateFormat.format(Date()),
        limit: Int = 10
    ): Deferred<List<Issue>> = CoroutineScope(Dispatchers.IO).async {
        val tag = "getIssuesByDate"
        log.debug("$tag issueDate: $issueDate limit: $limit")
        val issues = mutableListOf<Issue>()
        getDataDto(
            tag,
            QueryType.IssueByFeedAndDate,
            IssueVariables(issueDate = issueDate, limit = limit)
        ).product?.feedList?.forEach { feed ->
            issues.addAll(feed.issueList!!.map { Issue(feed.name!!, it) })
        }
        issues
    }

    /**
     * function to get [Issue]s by date
     * @param issueDate - the date of the issue last issue
     * @param limit - how many issues will be returned
     * @return [List] of [Issue] of the feed at given date
     */
    suspend fun getIssuesByDate(
        issueDate: String = simpleDateFormat.format(Date()),
        limit: Int = 10
    ): List<Issue> {
        val tag = "getIssuesByDate"
        log.debug("$tag issueDate: $issueDate limit: $limit")
        return transformExceptions(
            {
                val issues = mutableListOf<Issue>()
                graphQlClient.query(
                    QueryType.IssueByFeedAndDate,
                    IssueVariables(issueDate = issueDate, limit = limit)
                )?.product?.feedList?.forEach { feed ->
                    issues.addAll(feed.issueList!!.map { Issue(feed.name!!, it) })
                }
                issues.toList()
            }, tag
        ) ?: emptyList()
    }

    /**
     * function asynchronously to get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @param limit - how many issues will be returned
     * @return [Deferred]<[List]<[Issue]>> of the issues of a feed at given date
     */
    suspend fun getIssuesByFeedAndDateAsync(
        feedName: String = "taz",
        issueDate: String = simpleDateFormat.format(Date()),
        limit: Int = 2
    ): Deferred<List<Issue>> = CoroutineScope(Dispatchers.IO).async {
        val tag = "getIssuesByFeedAndDate"
        log.debug("$tag feedName: $feedName issueDate: $issueDate limit: $limit")
        getDataDto(
            tag,
            QueryType.IssueByFeedAndDate,
            IssueVariables(feedName, issueDate, limit)
        ).product?.feedList?.first()?.issueList?.map { Issue(feedName, it) } ?: emptyList()
    }

    /**
     * function to get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @param limit - how many issues will be returned
     * @return [List]<[Issue]> of the issues of a feed at given date
     */
    @Throws(ApiServiceException.NoInternetException::class)
    suspend fun getIssuesByFeedAndDate(
        feedName: String = "taz",
        issueDate: String = simpleDateFormat.format(Date()),
        limit: Int = 2
    ): List<Issue> {
        val tag = "getIssuesByFeedAndDate"
        log.debug("$tag feedName: $feedName issueDate: $issueDate limit: $limit")
        return transformExceptions({
            graphQlClient.query(
                QueryType.IssueByFeedAndDate, IssueVariables(feedName, issueDate, limit)
            )?.product?.feedList?.first()?.issueList?.map { Issue(feedName, it) } ?: emptyList()
        }, tag) ?: emptyList()
    }

    /**
     * function to get information about the current resources
     * @return [ResourceInfo] with the current [ResourceInfo.resourceVersion] and the information needed to download it
     */
    suspend fun getResourceInfoAsync(): Deferred<ResourceInfo?> =
        CoroutineScope(Dispatchers.IO).async {
            val tag = "getResourceInfo"
            log.debug(tag)
            getDataDto(tag, QueryType.ResourceInfo).product?.let { ResourceInfo(it) }
        }

    /**
     * function to inform server of started download
     * @param feedName name of the feed the download is started for
     * @param issueDate the date of the issue that is being downloaded
     * @return [String] the id of the download
     */
    suspend fun notifyServerOfDownloadStartAsync(
        feedName: String,
        issueDate: String
    ): Deferred<String?> = CoroutineScope(Dispatchers.IO).async {
        val tag = "notifyServerOfDownloadStart"
        getDataDto(
            tag,
            QueryType.DownloadStart,
            DownloadStartVariables(
                feedName,
                issueDate
            )
        ).downloadStart?.let { id ->
            log.debug("Notified server that download started. ID: $id")
            id
        }
    }

    /**
     * function to inform server of finished download
     * @param id the id of the download received via [notifyServerOfDownloadStartAsync]
     * @param time time in seconds needed for the download
     */
    suspend fun notifyServerOfDownloadStopAsync(
        id: String,
        time: Float
    ): Deferred<Boolean?> = CoroutineScope(Dispatchers.IO).async {
        val tag = "notifyServerOfDownloadStop"
        log.debug("$tag downloadId: $id time: $time")
        getDataDto(
            tag,
            QueryType.DownloadStop,
            DownloadStopVariables(id, time)
        ).downloadStop
    }

    /**
     * function to inform server the notification token
     * @param oldToken the old token of the string if any - will be removed on the server
     */
    suspend fun sendNotificationInfoAsync(oldToken: String? = null): Deferred<Boolean?> =
        CoroutineScope(Dispatchers.IO).async {
            val tag = "sendNotificationInfo"
            log.debug(tag)

            firebaseHelper.firebaseToken?.let { notificationToken ->
                if (notificationToken.isNotBlank()) {
                    getDataDto(
                        tag,
                        QueryType.Notification,
                        NotificationVariables(notificationToken, oldToken = oldToken)
                    ).notification
                } else {
                    false
                }
            }
        }

    /**
     * function to request a subscription
     * @param tazId the username
     * @param idPassword the password for the username
     * @param surname surname of the requesting person
     * @param firstName firstName of the requesting person
     */
    @Throws(ApiServiceException.NoInternetException::class)
    suspend fun subscription(
        tazId: String,
        idPassword: String,
        surname: String? = null,
        firstName: String? = null,
        street: String,
        city: String,
        postCode: String,
        country: String,
        phone: String? = null,
        price: Int,
        iban: String,
        accountHolder: String? = null,
        comment: String? = null,
        nameAffix: String? = null
    ): SubscriptionInfo? {
        val tag = "subscription"
        log.debug("$tag tazId: $tazId")
        return transformExceptions({
            val data = graphQlClient.query(
                QueryType.Subscription,
                SubscriptionVariables(
                    tazId = tazId,
                    idPassword = idPassword,
                    surname = surname,
                    firstName = firstName,
                    street = street,
                    city = city,
                    postcode = postCode,
                    country = country,
                    phone = phone,
                    price = price,
                    iban = iban,
                    accountHolder = accountHolder,
                    comment = comment,
                    nameAffix = nameAffix
                )
            )
            data?.subscription
        }, tag)
    }

    /**
     * function to request a trial subscription
     * @param tazId the username
     * @param idPassword the password for the username
     * @param surname surname of the requesting person
     * @param firstName firstName of the requesting person
     */
    @Throws(ApiServiceException.NoInternetException::class)
    suspend fun trialSubscription(
        tazId: String,
        idPassword: String,
        surname: String? = null,
        firstName: String? = null,
        nameAffix: String? = null
    ): SubscriptionInfo? {
        val tag = "trialSubscription"
        log.debug("$tag tazId: $tazId")
        return transformExceptions(
            {
                graphQlClient.query(
                    QueryType.TrialSubscription,
                    TrialSubscriptionVariables(
                        tazId = tazId,
                        idPassword = idPassword,
                        surname = surname,
                        firstName = firstName,
                        nameAffix = nameAffix
                    )
                )?.trialSubscription
            }, tag
        )
    }

    suspend fun sendErrorReportAsync(
        email: String?,
        message: String?,
        lastAction: String?,
        conditions: String?,
        storageType: String?,
        errorProtocol: String?,
        ramUsed: String?,
        ramAvailable: String?
    ): Deferred<Boolean?> = CoroutineScope(Dispatchers.IO).async {
        val tag = "sendErrorReport"
        log.debug("$tag email: $email message: $message lastAction: $lastAction conditions: $conditions storageType: $storageType")

        getDataDto(
            tag,
            QueryType.ErrorReport,
            ErrorReportVariables(
                email,
                message,
                lastAction,
                conditions,
                storageType,
                errorProtocol,
                ramUsed = ramUsed,
                ramAvailable = ramAvailable
            )
        ).errorReport
    }

    /**
     * function to request an email to reset the password
     * @param email the email of the account
     * @return [PasswordResetInfo] information if requesting has been successful
     */
    @Throws(
        ApiServiceException.NoInternetException::class
    )
    suspend fun requestCredentialsPasswordReset(
        email: String
    ): PasswordResetInfo? {
        val tag = "resetPassword"
        log.debug("$tag email: $email")

        return transformExceptions({
            graphQlClient.query(
                QueryType.PasswordReset,
                PasswordResetVariables(
                    email
                )
            )?.passwordReset
        }, tag)
    }

    @Throws(
        ApiServiceException.NoInternetException::class
    )
    suspend fun requestSubscriptionPassword(
        subscriptionId: Int
    ): SubscriptionResetInfo? {
        val tag = "resetPassword"
        log.debug("$tag email: $subscriptionId")

        return transformExceptions(
            {
                graphQlClient.query(
                    QueryType.SubscriptionReset,
                    SubscriptionResetVariables(
                        subscriptionId
                    )
                )?.subscriptionReset
            },
            tag
        )
    }

    suspend fun getPriceList(): List<PriceInfo> {
        val tag = "getPriceList"
        return transformExceptions(
            {
                graphQlClient.query(
                    QueryType.PriceList
                )?.priceList
            },
            tag
        ) ?: emptyList()
    }

    @Throws(ApiServiceException.NoInternetException::class)
    private suspend fun <T> transformExceptions(block: suspend () -> T, tag: String): T? {
        try {
            return block()
        } catch (se: SSLException) {
            log.debug("SSLException ${se.localizedMessage}")
            throw ApiServiceException.NoInternetException()
        } catch (eofe: EOFException) {
            log.debug("EOFException ${eofe.localizedMessage}")
            throw ApiServiceException.NoInternetException()
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
            log.error("QraphQl-Error:\n", jee)
            Sentry.capture(ApiServiceException.WrongDataException())
            toastHelper.showSomethingWentWrongToast()
        } catch (npe: NullPointerException) {
            // inform sentry of missing data in response
            log.error("QraphQl-Error:\n", npe)
            Sentry.capture(ApiServiceException.InsufficientDataException(tag))
            toastHelper.showSomethingWentWrongToast()
        }
        return null
    }

    private suspend
    fun <T> catchExceptions(block: suspend () -> T, tag: String): T? {
        try {
            return block()
        } catch (eofe: EOFException) {
            log.debug("EOFException ${eofe.localizedMessage}")
            serverConnectionHelper.isServerReachable = false
        } catch (uhe: UnknownHostException) {
            log.debug("UnknownHostException ${uhe.localizedMessage}")
            serverConnectionHelper.isServerReachable = false
        } catch (ce: ConnectException) {
            log.debug("ConnectException ${ce.localizedMessage}")
            serverConnectionHelper.isServerReachable = false
        } catch (ste: SocketTimeoutException) {
            log.debug("SocketTimeoutException ${ste.localizedMessage}")
            serverConnectionHelper.isServerReachable = false
        } catch (jee: JsonEncodingException) {
            // inform sentry of malformed JSON response
            Sentry.capture(ApiServiceException.WrongDataException())
            toastHelper.showSomethingWentWrongToast()
        } catch (npe: NullPointerException) {
            // inform sentry of missing data in response
            Sentry.capture(ApiServiceException.InsufficientDataException(tag))
            toastHelper.showSomethingWentWrongToast()
        } catch (se: SSLException) {
            log.debug("SSLException ${se.localizedMessage}")
            serverConnectionHelper.isServerReachable = false
        }
        return null
    }

    private suspend
    fun waitForInternet(tag: String) = suspendCoroutine<Unit> { continuation ->
        if (serverConnectionHelper.isServerReachable) {
            continuation.resume(Unit)
        } else {
            log.debug("ApiCall $tag waiting")
            waitInternetList.add(continuation)
        }
    }

    private suspend fun getDataDto(
        tag: String,
        queryType: QueryType,
        variables: Variables? = null
    ): DataDto {
        log.debug(tag)
        var result: DataDto? = null
        while (result == null) {
            waitForInternet(tag)
            result = catchExceptions(
                {
                    val data = graphQlClient.query(queryType, variables)
                    updateAuthStatus(data?.product)
                    data
                }, tag
            )
        }
        return result
    }

    /**
     * function to request an email with the subscription password
     * @param subscriptionId the if of the subscription
     * @return
     */
    /**
     * if product returns authStatus update it in the authHelper
     */
    private fun updateAuthStatus(product: ProductDto?): ProductDto? {
        product?.authInfo?.let {
            authHelper.authStatus = it.status
        }
        return product
    }

    object ApiServiceException {
        class InsufficientDataException(function: String) :
            Exception("ApiService.$function failed.")

        class NoInternetException : Exception("no internet connection")
        class WrongDataException : Exception("data could not be parsed")
    }

}