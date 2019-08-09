package de.taz.app.android.api

import de.taz.app.android.api.models.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Exception

/**
 * Service class to get Models from GraphQl
 * The DTO objects returned by [GraphQlClient] will be transformed to models here
 */
class ApiService(
    private val graphQlClient: GraphQlClient = GraphQlClient()
) {

    private val dateHelper = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * function to authenticate with the backend
     * @param user - username or id of the user
     * @param password - the password of the user
     * @return [AuthTokenInfo] indicating if authentication has been successful and with token if successful
     */
    suspend fun authenticate(user: String, password: String): AuthTokenInfo {
        try {
            return graphQlClient.query(
                QueryType.AuthenticationQuery,
                mapOf(
                    "user" to user, "password" to password
                )
            ).authentificationToken!!
        } catch (e: Exception) {
            // TODO catch correct exception
            throw ApiServiceException.InsufficientData()
        }
    }

    /**
     * function to get the app info
     * @return [AppInfo] with [AppInfo.appName] and [AppInfo.appType]
     */
    suspend fun getAppInfo(): AppInfo {
        try {
            return AppInfo(graphQlClient.query(QueryType.AppInfoQuery).product!!)
        } catch (e: Exception) {
            // TODO catch correct exception
            throw ApiServiceException.InsufficientData()
        }
    }

    /**
     * function to get current authentication information
     * @return [AuthInfo] indicating if authenticated and if not why not
     */
    suspend fun getAuthInfo(): AuthInfo {
        try {
            return graphQlClient.query(QueryType.AuthInfoQuery).product!!.authInfo!!
        } catch (e: Exception) {
            // TODO catch correct exception
            throw ApiServiceException.InsufficientData()
        }
    }

    /**
     * function to get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @return [Issue] of the feed at given date
     */
    suspend fun getIssueByFeedAndDate(feedName: String = "taz", issueDate: Date = Date()) : Issue {
        return graphQlClient.query(
            QueryType.IssueByFeedAndDateQuery,
            mapOf(
                "feedName" to feedName,
                "issueDate" to dateHelper.format(issueDate)
            )
        ).product!!.feedList!!.first().issueList!!.first()
    }

    /**
     * function to get information about the current resources
     * @return [ResourceInfo] with the current [ResourceInfo.resourceVersion] and the information needed to download it
     */
    suspend fun getResourceInfo(): ResourceInfo {
        try {
            return ResourceInfo(graphQlClient.query(QueryType.ResourceInfoQuery).product!!)
        } catch (e: Exception) {
            // TODO catch correct exception
            throw ApiServiceException.InsufficientData()
        }
    }

    object ApiServiceException {
        class InsufficientData : Exception("Unable to cast. Missing data in DTO model.")
    }

}