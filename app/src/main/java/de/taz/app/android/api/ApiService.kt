package de.taz.app.android.api

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.vdurmont.semver4j.Semver
import de.taz.app.android.CUSTOMER_DATA_CATEGORY_BOOKMARKS
import de.taz.app.android.CUSTOMER_DATA_CATEGORY_BOOKMARKS_ALL
import de.taz.app.android.CUSTOMER_DATA_VAL_DATE
import de.taz.app.android.R
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.dto.*
import de.taz.app.android.api.mappers.*
import de.taz.app.android.api.models.*
import de.taz.app.android.api.variables.*
import de.taz.app.android.data.INFINITE
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.firebase.FirebaseDataStore
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.NotFoundException
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.util.SingletonHolder
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * Service class to get Models from GraphQl
 * The DTO objects returned by [GraphQlClient] will be transformed to models here
 */
@Mockable
class ApiService @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) constructor(
    private val graphQlClient: GraphQlClient,
    private val authHelper: AuthHelper,
    private val fireBaseDataStore: FirebaseDataStore,
    private val deviceFormat: DeviceFormat,
    private val downloadDataStore: DownloadDataStore,
) {

    private constructor(applicationContext: Context) : this(
        graphQlClient = GraphQlClient.getInstance(applicationContext),
        authHelper = AuthHelper.getInstance(applicationContext),
        fireBaseDataStore = FirebaseDataStore.getInstance(applicationContext),
        deviceFormat = if (applicationContext.resources.getBoolean(R.bool.isTablet)) {
            DeviceFormat.tablet
        } else {
            DeviceFormat.mobile
        },
        downloadDataStore = DownloadDataStore.getInstance(applicationContext)
    )

    companion object : SingletonHolder<ApiService, Context>(::ApiService)

    private val connectionHelper = APIConnectionHelper(graphQlClient)

    /**
     * Wrap a block potentially throwing [ConnectivityException.Recoverable] in a [connectionHelper]
     * to manage retries
     */
    suspend fun <T> retryOnConnectionFailure(
        onConnectionFailure: suspend () -> Unit = {},
        maxRetries: Int = INFINITE,
        block: suspend () -> T
    ): T {
        return connectionHelper.retryOnConnectivityFailure({
            onConnectionFailure()
        }, maxRetries) {
            block()
        }
    }

    /**
     * function to check whether the graph ql endpoint is reachable
     */
    suspend fun checkForConnectivity(): Boolean {
        return connectionHelper.checkConnectivity()
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
    @Throws(ConnectivityException::class)
    suspend fun subscriptionId2TazId(
        tazId: String,
        idPassword: String,
        subscriptionId: Int,
        subscriptionPassword: String,
        surname: String? = null,
        firstName: String? = null
    ): SubscriptionInfo? {
        return transformToConnectivityException {
            graphQlClient.query(
                QueryType.SubscriptionId2TazId,
                SubscriptionId2TazIdVariables(
                    installationId = authHelper.installationId.get(),
                    pushToken = fireBaseDataStore.token.get(),
                    tazId = tazId,
                    idPassword = idPassword,
                    subscriptionId = subscriptionId,
                    subscriptionPassword = subscriptionPassword,
                    surname = surname,
                    firstName = firstName,
                    deviceFormat = deviceFormat
                )
            ).data
                ?.subscriptionId2tazId
                ?.let { SubscriptionInfoMapper.from(it) }
        }
    }

    @Throws(ConnectivityException::class)
    suspend fun subscriptionPoll(): SubscriptionInfo? {
        val tag = "subscriptionPoll"
        log.debug(tag)
        return transformToConnectivityException {
            graphQlClient.query(
                QueryType.SubscriptionPoll,
                SubscriptionPollVariables(
                    installationId = authHelper.installationId.get(),
                    deviceFormat = deviceFormat
                )
            ).data
                ?.subscriptionPoll
                ?.let { SubscriptionInfoMapper.from(it) }
        }
    }

    /**
     * function to authenticate with the backend
     * @param user - username or id of the user
     * @param password - the password of the user
     * @return [AuthTokenInfo] indicating if authentication has been successful and with token if successful
     */
    // TODO only return authTokenInfo again once elapsed trialSubscription login returns token
    suspend fun authenticate(user: String, password: String): AuthTokenInfo? {
        val tag = "authenticate"
        log.debug("$tag username: $user")

        val response = transformToConnectivityException {
            graphQlClient.query(
                QueryType.Authentication,
                AuthenticationVariables(
                    user = user,
                    password = password,
                    deviceFormat = deviceFormat
                )
            ).data?.authentificationToken
        }

        return response?.let { AuthTokenInfoMapper.from(it) }
    }

    /**
     * function to verify if an subscriptionId password combination is valid
     * @param subscriptionId- the id of the subscription of the user
     * @param password - the password of the user
     * @return [AuthInfo] indicating if combination is valid, elapsed or invalid
     */
    @Throws(ConnectivityException::class)
    suspend fun checkSubscriptionId(
        subscriptionId: Int,
        password: String
    ): AuthInfo? = transformToConnectivityException {
        val response = graphQlClient.query(
            QueryType.CheckSubscriptionId,
            CheckSubscriptionIdVariables(
                subscriptionId = subscriptionId,
                password = password,
                deviceFormat = deviceFormat
            ),
        ).data?.checkSubscriptionId
        response?.let { AuthInfoMapper.from(it) }
    }


    /**
     * function to get the app info
     * @return [AppInfo] with [AppInfo.appName] and [AppInfo.appType]
     */
    suspend fun getAppInfo(): AppInfo = transformToConnectivityException {
        val productDto = graphQlClient.query(QueryType.AppInfo).data?.product
        if (productDto != null) {
            AppInfoMapper.from(productDto)
        } else {
            throw ConnectivityException.ImplementationException("Unexpected response while retrieving AppInfo")
        }
    }

    /**
     * function to get available feeds
     * @return List of [Feed]s
     */
    @Throws(ConnectivityException::class)
    suspend fun getFeedByName(name: String): Feed? = transformToConnectivityException {
        graphQlClient.query(
            QueryType.Feed,
            FeedVariables(feedName = name)
        ).data?.product?.feedList?.map {
            FeedMapper.from(it)
        }?.firstOrNull()
    }

    /**
     * function to get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @return [Issue] of the feed at given date
     */
    @Throws(ConnectivityException::class)
    suspend fun getIssueByFeedAndDate(
        feedName: String,
        issueDate: Date
    ): Issue {
        val tag = "getIssueByFeedAndDate"
        log.debug("$tag feedName: $feedName issueDate: $issueDate")
        val issueList = getIssuesByFeedAndDate(feedName, issueDate, 1)
        try {
            return issueList.first { it.date == simpleDateFormat.format(issueDate) }
        } catch (e: NoSuchElementException) {
            throw NotFoundException("Issue ($issueDate) not found on API")
        }
    }

    /**
     * function to get the last [Issue]s
     * @param limit - number of issues to get
     * @return [List]<[Issue]>
     */
    @Throws(ConnectivityException::class)
    suspend fun getLastIssues(limit: Int = 10): List<Issue> {
        val issues = mutableListOf<Issue>()
        transformToConnectivityException {
            graphQlClient.query(
                QueryType.LastIssues,
                IssueVariables(limit = limit)
            ).data?.product?.feedList?.forEach { feed ->
                val feedName = requireNotNull(feed.name)
                val feedIssues = feed.issueList
                    ?.map { IssueMapper.from(feedName, it) }
                    ?: emptyList()
                issues.addAll(feedIssues)
            }
        }
        return issues
    }

    /**
     * Assembles a search query
     */
    suspend fun search(
        searchText: String,
        title: String?,
        author: String?,
        sessionId: String? = null,
        rowCnt: Int = 20,
        offset: Int = 0,
        pubDateFrom: String? = null,
        pubDateUntil: String? = null,
        filter: SearchFilter = SearchFilter.all,
        sorting: Sorting = Sorting.relevance
    ): Search? {
        return transformToConnectivityException {
            graphQlClient.query(
                QueryType.Search,
                SearchVariables(
                    text = searchText,
                    title = title,
                    author = author,
                    sessionId = sessionId,
                    rowCnt = rowCnt,
                    offset = offset,
                    pubDateFrom = pubDateFrom,
                    pubDateUntil = pubDateUntil,
                    filter = filter,
                    sorting = sorting,
                    deviceFormat = deviceFormat
                )
            ).data?.search
        }?.let { searchDto ->
            SearchMapper.from(searchDto)
        }
    }

    /**
     * function to get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @param limit - how many issues will be returned
     * @return [List]<[Issue]> of the issues of a feed at given date
     */
    @Throws(ConnectivityException::class)
    suspend fun getIssuesByFeedAndDate(
        feedName: String,
        issueDate: Date,
        limit: Int = 2
    ): List<Issue> {
        val tag = "getIssuesByFeedAndDate"
        log.debug("$tag feedName: $feedName issueDate: $issueDate limit: $limit")
        val dateString = simpleDateFormat.format(issueDate)
        return transformToConnectivityException {
            graphQlClient.query(
                QueryType.IssueByFeedAndDate, IssueVariables(feedName, dateString, limit)
            ).data?.product
                ?.feedList
                ?.first()
                ?.issueList
                ?.map { IssueMapper.from(feedName, it) }
                ?: emptyList()
        }
    }

    /**
     * function to get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @return [List]<[Issue]> of the issues of a feed at given date
     */
    @Throws(ConnectivityException::class)
    suspend fun getMomentByFeedAndDate(
        feedName: String,
        issueDate: Date
    ): Moment? {
        val dateString = simpleDateFormat.format(issueDate)
        return transformToConnectivityException {
            graphQlClient.query(
                QueryType.Moment, IssueVariables(feedName, dateString, 1)
            ).data?.product
                ?.feedList
                ?.first()
                ?.issueList
                ?.first()
                ?.let {
                    val status = IssueStatusMapper.from(it.status)
                    MomentMapper.from(
                        IssueKey(feedName, dateString, status),
                        it.baseUrl,
                        it.moment
                    )
                }
        }
    }

    /**
     * // TODO it seems wasteful to request a whole issue for the single purpose of getting the frontpage
     * function to get the front page of an issue by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - date of an issue
     * @return [List]<[Issue]> of the issues of a feed at given date
     */
    @Throws(ConnectivityException::class)
    suspend fun getFrontPageByFeedAndDate(
        feedName: String,
        issueDate: Date
    ): Pair<Page, IssueStatus>? {
        val dateString = simpleDateFormat.format(issueDate)
        return transformToConnectivityException {
            graphQlClient.query(
                QueryType.IssueByFeedAndDate, IssueVariables(feedName, dateString, 1)
            ).data?.product
                ?.feedList
                ?.first()
                ?.issueList
                ?.first()
                ?.let { issue ->
                    issue.pageList?.firstOrNull()
                        ?.let { pageDto ->
                            val status = IssueStatusMapper.from(issue.status)
                            val page = PageMapper.from(
                                IssueKey(feedName, dateString, status),
                                issue.baseUrl,
                                pageDto
                            )
                            page to status
                        }
                }
        }
    }

    /**
     * function to get information about the current resources
     * @return [ResourceInfo] with the current [ResourceInfo.resourceVersion] and the information needed to download it
     */
    suspend fun getResourceInfo(): ResourceInfo {
        val productDto = transformToConnectivityException {
            graphQlClient.query(QueryType.ResourceInfo).data?.product
        }
        if (productDto != null) {
            return ResourceInfoMapper.from(productDto)
        } else {
            throw ConnectivityException.ImplementationException("Unexpected response while retrieving AppInfo")
        }
    }


    /**
     * function to inform server of started download
     * @param feedName name of the feed the download is started for
     * @param issueDate the date of the issue that is being downloaded
     * @param isAutomatically indicates whether the download was started automatically (i.e.
     *    by notification) or manually by the user
     * @return [String] the id of the download
     */
    suspend fun notifyServerOfDownloadStart(
        feedName: String,
        issueDate: String,
        isAutomatically: Boolean
    ): String? {
        return transformToConnectivityException {
            graphQlClient.query(
                QueryType.DownloadStart,
                DownloadStartVariables(
                    feedName = feedName,
                    issueDate = issueDate,
                    isAutomatically = isAutomatically,
                    installationId = authHelper.installationId.get(),
                    isPush = fireBaseDataStore.isPush(),
                    pushToken = fireBaseDataStore.token.get(),
                    deviceFormat = deviceFormat,
                    textNotification = downloadDataStore.notificationsEnabled.get()
                )
            ).data?.downloadStart?.let { id ->
                log.debug("Notified server that download started. ID: $id with pushToken: ${fireBaseDataStore.token.get()}")
                id
            }
        }
    }

    /**
     * function to inform server of finished download
     * @param id the id of the download received via [notifyServerOfDownloadStart]
     * @param time time in seconds needed for the download
     */
    @Throws(ConnectivityException::class)
    suspend fun notifyServerOfDownloadStop(
        id: String,
        time: Float
    ) {
        transformToConnectivityException {
            graphQlClient.query(
                QueryType.DownloadStop,
                DownloadStopVariables(
                    id = id,
                    time = time,
                    deviceFormat = deviceFormat
                )
            )
        }
    }

    /**
     * function to inform server the notification token
     * @param oldToken the old token of the string if any - will be removed on the server
     */
    suspend fun sendNotificationInfo(token: String, oldToken: String? = null): Boolean =
        transformToConnectivityException {
            graphQlClient.query(
                QueryType.Notification,
                NotificationVariables(
                    pushToken = token,
                    oldToken = oldToken,
                    deviceFormat = deviceFormat
                )
            ).data?.notification
                ?: throw ConnectivityException.ImplementationException("Expected notification in response to send notification query")
        }

    /**
     * function to inform server that text notifications are enabled or not
     * @param [enabled] Boolean indicating that text notifications are allowed
     */
    suspend fun setNotificationsEnabled(enabled: Boolean): Boolean {
        val pushToken = fireBaseDataStore.token.get()
        return if (pushToken.isNullOrBlank()) {
            val hint = "No pushToken is found. It is necessary to use when enabling notifications"
            log.warn(hint)
            SentryWrapper.captureMessage(hint)
            false
        } else {
            transformToConnectivityException {
                graphQlClient.query(
                    QueryType.Notification,
                    NotificationVariables(
                        pushToken = pushToken,
                        deviceFormat = deviceFormat,
                        textNotification = enabled,
                    )
                ).data?.notification
                    ?: throw ConnectivityException.ImplementationException("Expected notification in response to send notification query")
            }
        }
    }

    /**
     * function to request a trial subscription
     * @param tazId the username
     * @param idPassword the password for the username
     * @param surname surname of the requesting person
     * @param firstName firstName of the requesting person
     */
    @Throws(ConnectivityException::class)
    suspend fun trialSubscription(
        tazId: String,
        idPassword: String,
        surname: String? = null,
        firstName: String? = null,
    ): SubscriptionInfo? {
        val tag = "trialSubscription"
        log.debug("$tag tazId: $tazId")
        return transformToConnectivityException {
            graphQlClient.query(
                QueryType.TrialSubscription,
                TrialSubscriptionVariables(
                    tazId = tazId,
                    idPassword = idPassword,
                    surname = surname,
                    firstName = firstName,
                    installationId = authHelper.installationId.get(),
                    pushToken = fireBaseDataStore.token.get(),
                    deviceFormat = deviceFormat
                )
            ).data
                ?.trialSubscription
                ?.let { SubscriptionInfoMapper.from(it) }
        }
    }

    suspend fun sendErrorReport(
        email: String?,
        message: String?,
        lastAction: String?,
        conditions: String?,
        storageType: String?,
        errorProtocol: String?,
        ramUsed: String?,
        ramAvailable: String?,
        screenshotName: String?,
        screenshot: String?
    ) {
        val tag = "sendErrorReport"
        log.debug(
            "$tag email: $email " +
                    "message: $message " +
                    "lastAction: $lastAction " +
                    "conditions: $conditions " +
                    "storageType: $storageType " +
                    "errorProtocol: $errorProtocol " +
                    "ramUsed: $ramUsed " +
                    "ramAvailable: $ramAvailable " +
                    "screenshotName: $screenshotName " +
                    "screenshot: $screenshot "
        )
        transformToConnectivityException {
            graphQlClient.query(
                QueryType.ErrorReport,
                ErrorReportVariables(
                    installationId = authHelper.installationId.get(),
                    pushToken = fireBaseDataStore.token.get(),
                    eMail = email,
                    message = message,
                    lastAction = lastAction,
                    conditions = conditions,
                    storageType = storageType,
                    errorProtocol = errorProtocol,
                    ramUsed = ramUsed,
                    ramAvailable = ramAvailable,
                    screenshotName = screenshotName,
                    screenshot = screenshot,
                    deviceFormat = deviceFormat
                )
            )
        }
    }

    /**
     * function to request an email to reset the password
     * @param email the email of the account
     * @return [PasswordResetInfo] information if requesting has been successful
     */
    @Throws(
        ConnectivityException::class
    )
    suspend fun requestCredentialsPasswordReset(
        email: String
    ): PasswordResetInfo? {
        val tag = "resetPassword"
        log.debug("$tag email: $email")

        return transformToConnectivityException {
            graphQlClient.query(
                QueryType.PasswordReset,
                PasswordResetVariables(
                    email = email,
                    deviceFormat = deviceFormat
                )
            ).data?.passwordReset
        }
    }

    @Throws(
        ConnectivityException::class
    )
    suspend fun requestSubscriptionPassword(
        subscriptionId: Int
    ): SubscriptionResetInfo? {
        val tag = "resetPassword with subscriptionId"
        log.debug("$tag subscriptionId: $subscriptionId")

        return transformToConnectivityException {
            graphQlClient.query(
                QueryType.SubscriptionReset,
                SubscriptionResetVariables(
                    subscriptionId = subscriptionId,
                    deviceFormat = deviceFormat
                )
            ).data
                ?.subscriptionReset
                ?.let { SubscriptionResetInfoMapper.from(it) }
        }
    }

    suspend fun getIssueByPublication(issuePublication: AbstractIssuePublication): Issue =
        transformToConnectivityException {
            val issues = graphQlClient.query(
                QueryType.IssueByFeedAndDate,
                IssueVariables(
                    issueDate = issuePublication.date,
                    feedName = issuePublication.feedName,
                    limit = 1
                )
            )

            issues.data?.product
                ?.feedList
                ?.firstOrNull()
                ?.issueList
                ?.firstOrNull()
                ?.let {
                    IssueMapper.from(issuePublication.feedName, it)
                }
        } ?: throw NotFoundException("Issue (${issuePublication.date}) not found on API")


    /**
     * function to start a cancellation. The id is taken from the JWT
     */
    @Throws(ConnectivityException::class)
    suspend fun cancellation(isForce: Boolean = false): CancellationStatus? {
        val tag = "cancellation"
        log.debug("graphql call: $tag with isForce $isForce")
        return transformToConnectivityException {
            graphQlClient.query(
                QueryType.Cancellation,
                CancellationVariables(
                    isForce
                )
            ).data
                ?.cancellation
                ?.let { CancellationStatusMapper.from(it) }
        }
    }

    /**
     * function to trigger mutation on subscription form data
     */
    @Throws(ConnectivityException::class)
    suspend fun subscriptionFormData(
        type: SubscriptionFormDataType,
        mail: String? = null,
        subscriptionId: Int? = null,
        surname: String? = null,
        firstname: String? = null,
        street: String? = null,
        city: String? = null,
        postcode: String? = null,
        country: String? = null,
        message: String? = null,
        requestCurrentSubscriptionOpportunities: Boolean? = null,
    ): SubscriptionFormData {
        val tag = "subscriptionFormData"
        val variables = SubscriptionFormDataVariables(
            type,
            mail,
            subscriptionId,
            surname,
            firstname,
            street,
            city,
            postcode,
            country,
            message,
            requestCurrentSubscriptionOpportunities,
            deviceFormat = deviceFormat
        )
        log.debug("call graphql  $tag with variables: $variables")
        return transformToConnectivityException {
            graphQlClient.query(
                QueryType.SubscriptionFormData,
                variables
            )
        }.data
            ?.subscriptionFormData
            ?.let { SubscriptionFormDataMapper.from(it) }
            ?: throw Exception("Missing SubscriptionFormData response - was null")
    }

    /**
     * function to get the customer type
     */
    @Throws(ConnectivityException::class)
    suspend fun getCustomerType(): CustomerType? {
        val tag = "customerInfo"
        log.debug("call graphql  $tag")
        val response = transformToConnectivityException {
            graphQlClient.query(
                QueryType.CustomerInfo
            ).data?.customerInfo?.customerType
        }

        return response?.let { CustomerTypeMapper.from(it) }
    }

    /**
     * Query the minimum required app version.
     */
    @Throws(ConnectivityException::class)
    suspend fun getMinAppVersion(): Semver? {
        val variables = AppVariables(os="Android", type="native")
        val appResponse = transformToConnectivityException {
            graphQlClient.query(
                QueryType.App,
                variables
            ).data?.app
        }
        return appResponse?.let { MinAppVersionMapper.from(it) }
    }

    // region bookmark synchronization functions

    /**
     * Call getCustomerData with category="bookmarks" will hand us a list of synchronized bookmarks
     */
    suspend fun getSynchronizedBookmarks(): List<BookmarkRepresentation>? {

        val variables = GetCustomerDataVariables(
            CUSTOMER_DATA_CATEGORY_BOOKMARKS, CUSTOMER_DATA_CATEGORY_BOOKMARKS_ALL
        )
        // Get customer data
        val getCustomerDataResponse = transformToConnectivityException {
            graphQlClient.query(
                QueryType.GetCustomerData,
                variables,
            ).data?.getCustomerData
        }

        // Parse it to list of articleMediaSyncIds with dates:
        val listOfArticles = getCustomerDataResponse?.let { response ->
            response.customerDataList?.filter { customerDataList ->
                customerDataList.category == CUSTOMER_DATA_CATEGORY_BOOKMARKS
            }?.mapNotNull { filteredList ->
                val mediaSyncId = filteredList.name.toInt()
                val date = parseDate(filteredList.`val`)
                if (date != null) {
                    BookmarkRepresentation(mediaSyncId, date)
                } else {
                    null
                }
            } ?: emptyList()
        }
        return listOfArticles
    }

    /**
     * Delete from customerData the passed [articleMediaSyncId]
     */
    suspend fun deleteRemoteBookmark(articleMediaSyncId: Int): Boolean {
        val variables = GetCustomerDataVariables(
            CUSTOMER_DATA_CATEGORY_BOOKMARKS, articleMediaSyncId.toString()
        )

        val response = transformToConnectivityException {
            graphQlClient.query(
                QueryType.DeleteCustomerData,
                variables,
            ).data?.deleteCustomerData
        }
        if (response?.ok == false) {
            log.warn("Could not delete remote bookmark: ${response.error}")
        }
        if (response?.ok == true) {
            log.verbose("Remote bookmark $articleMediaSyncId successfully deleted.")
            return true
        } else {
            return false
        }
    }

    /**
     * Add from customerData the passed [articleMediaSyncId] and [articleDate].
     * The date needs to be jsonified to be handled by the server correctly, in a format like
     * "{"date":"2024-10-07"}"
     */
    suspend fun saveSynchronizedBookmark(articleMediaSyncId: Int, articleDate: String): Boolean {
        val jsonDate = JSONObject().put(CUSTOMER_DATA_VAL_DATE, articleDate)
        val variables = SaveCustomerDataVariables(
            CUSTOMER_DATA_CATEGORY_BOOKMARKS, articleMediaSyncId.toString(), jsonDate.toString()
        )
        val response = transformToConnectivityException {
            graphQlClient.query(
                QueryType.SaveCustomerData,
                variables,
            ).data?.saveCustomerData
        } ?: return false
        if (response.ok == true) {
            log.verbose("bookmark of $articleMediaSyncId successfully synchronized.")
            return true
        } else {
            log.warn("Could not save bookmark to remote: ${response.error}")
            return false
        }
    }

    /**
     * Parse a json string to just return the string of the date.
     * E.g. parseDate( "{"date":"2024-10-07"}") would return "2024-10-07"
     */
    private fun parseDate(jsonString: String?): String? {
        val date = try {
            val jsonValue = jsonString?.let { JSONObject(it) }
            jsonValue?.optString(CUSTOMER_DATA_VAL_DATE)
        } catch (e: JSONException) {
            log.warn("could not parse  $jsonString")
            null
        }
        return date
    }
    // endregion
}
