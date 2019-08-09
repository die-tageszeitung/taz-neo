package de.taz.app.android.api

import android.content.Context
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Types of queries that are available
 * The names need to match a file with `.graphql` extension in the assets
 */
enum class QueryType {
    AppInfoQuery,
    AuthenticationQuery,
    AuthInfoQuery,
    FeedQuery,
    IssueByFeedAndDateQuery,
    ResourceInfoQuery
}

/**
 * service class to get the queries either from cache or by reading the assets file
 */
open class QueryService private constructor(private val applicationContext: Context) {

    companion object : SingletonHolder<QueryService, Context>(::QueryService)

    private val log by Log
    private val queryCache = mutableMapOf<String, String>()

    open fun get(queryType: QueryType): Query {
        val fileName = queryType.name
        return Query(try {
            queryCache[fileName] ?: readGraphQlQueryFromAssets(fileName)
        } catch (e: IOException) {
            log.error("reading $fileName.graphql failed")
            throw e
        })
    }


    @Throws(IOException::class)
    private fun readGraphQlQueryFromAssets(fileName: String): String {
        val fullFilePath = if (fileName.endsWith(".graphql")) fileName else "$fileName.graphql"

        var bufferedReader: BufferedReader? = null
        var data = ""
        try {
            bufferedReader = BufferedReader(
                InputStreamReader(
                    applicationContext.assets.open(fullFilePath),
                    "UTF-8"
                )
            )

            var line: String? = bufferedReader.readLine()
            while (line != null) {
                data += line
                line = bufferedReader.readLine()
            }
        } finally {
            bufferedReader?.close()
        }
        return data
    }
}