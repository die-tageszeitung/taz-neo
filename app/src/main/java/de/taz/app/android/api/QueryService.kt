package de.taz.app.android.api

import android.content.Context
import androidx.annotation.VisibleForTesting
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import java.io.IOException

/**
 * Types of queries that are available
 * The names need to match a file with `.graphql` extension in the assets
 */
enum class QueryType {
    AppInfoQuery,
    AuthenticationQuery,
    AuthInfoQuery,
    DownloadStart,
    DownloadStop,
    FeedQuery,
    IssueByFeedAndDateQuery,
    ResourceInfoQuery
}

/**
 * service class to get the queries either from cache or by reading the assets file
 */
open class QueryService private constructor(applicationContext: Context) {

    companion object : SingletonHolder<QueryService, Context>(::QueryService)

    private val log by Log
    private val fileHelper = FileHelper.getInstance(applicationContext)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val queryCache = mutableMapOf<String, String>()

    open fun get(queryType: QueryType): Query {
        val fileName = queryType.name
        return Query(try {
            queryCache[fileName] ?: let {
                val queryString = readGraphQlQueryFromAssets(fileName)
                queryCache[fileName] = queryString
                queryString
            }
        } catch (e: IOException) {
            log.error("reading $fileName.graphql failed")
            throw e
        })
    }


    @Throws(IOException::class)
    private fun readGraphQlQueryFromAssets(fileName: String): String {
        val fullFilePath =
            "graphql/" + if (fileName.endsWith(".graphql")) fileName else "$fileName.graphql"
        return fileHelper.readFileFromAssets(fullFilePath)
    }
}