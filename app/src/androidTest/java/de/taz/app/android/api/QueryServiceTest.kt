package de.taz.app.android.api

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

import org.junit.Before

class QueryServiceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var queryService: QueryService

    @Before
    fun beforeEach() {
        queryService = QueryService.createInstance(context)
    }

    @Test
    fun allQueriesExist() {
        val queryValues = QueryType.values()
        queryValues.forEach {
            println(it.name)
            queryService.get(it)
        }
    }
}