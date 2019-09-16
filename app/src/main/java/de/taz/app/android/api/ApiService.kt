package de.taz.app.android.api

import de.taz.app.android.api.models.*
import java.text.SimpleDateFormat
import java.util.*

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
        } catch (npe: NullPointerException) {
            throw ApiServiceException.InsufficientData("authenticate")
        }
    }

    /**
     * function to get the app info
     * @return [AppInfo]
     */
    suspend fun getAppInfo(): AppInfo {
        try {
            return AppInfo(graphQlClient.query(QueryType.AppInfoQuery).product!!)
        } catch (npe: NullPointerException) {
            throw ApiServiceException.InsufficientData("getAppInfo")
        }
    }

    /**
     * function to get current authentication information
     * @return [AuthInfo] indicating if authenticated and if not why not
     */
    suspend fun getAuthInfo(): AuthInfo {
        try {
            return graphQlClient.query(QueryType.AuthInfoQuery).product!!.authInfo!!
        } catch (npe: NullPointerException) {
            throw ApiServiceException.InsufficientData("getAuthInfo")
        }
    }

    /**
     * function to get an [Issue] by feedName and date
     * @param feedName - the name of the feed
     * @param issueDate - the date of the issue
     * @return [Issue] of the feed at given date
     */
    suspend fun getIssueByFeedAndDate(feedName: String = "taz", issueDate: Date = Date()) : Issue {
        try{
            return Issue(feedName, graphQlClient.query(
                QueryType.IssueByFeedAndDateQuery,
                mapOf(
                    "feedName" to feedName,
                    "issueDate" to dateHelper.format(issueDate)
                )
            ).product!!.feedList!!.first().issueList!!.first())
        } catch (npe: NullPointerException) {
            throw ApiServiceException.InsufficientData("getIssueByFeedAndDate")
        }
    }

    /**
     * function to get information about the current resources
     * @return [ResourceInfo] with the current [ResourceInfo.resourceVersion] and the information needed to download it
     */
    suspend fun getResourceInfo(): ResourceInfo {
        try {
            return ResourceInfo(graphQlClient.query(QueryType.ResourceInfoQuery).product!!)
        } catch (npe: NullPointerException) {
            throw ApiServiceException.InsufficientData("getResourceInfo")
        }
    }

    object ApiServiceException {
        class InsufficientData(function: String) : Exception("ApiService.$function failed.")
    }

}