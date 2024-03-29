package de.taz.app.android.api

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

import org.junit.Before

class QueryServiceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var queryService: QueryService

    @Before
    fun beforeEach() {
        queryService = QueryService.getInstance(context)
    }

    @Test
    fun allQueriesExist() {
        val queryValues = QueryType.values()
        queryValues.forEach {
            println(it.name)
            runBlocking { queryService.get(it) }
        }
    }


    @Test
    fun usesCache() {
        runBlocking { queryService.get(QueryType.AppInfo) }
        Assert.assertFalse(queryService.queryCache[QueryType.AppInfo.name].isNullOrEmpty())
    }


}