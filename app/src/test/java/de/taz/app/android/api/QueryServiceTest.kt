package de.taz.app.android.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonsUtil
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class QueryServiceTest {

    private lateinit var queryService: QueryService

    @Before
    fun beforeEach() {
        SingletonsUtil.resetAll()

        val context = ApplicationProvider.getApplicationContext<Context>()
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