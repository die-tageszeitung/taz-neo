package de.taz.app.android.api

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test

class QueryTest {

    private val moshi = Moshi.Builder().build()
    private val jsonAdapter = moshi.adapter(QueryTestHelper::class.java)

    @Test
    fun withVariables() {
        val queryString = "query AppInfoQuery { product { appName }}"
        var instance = Query(queryString)
        val variables = mapOf("foo" to "bar", "AC" to "AB")
        instance = instance.setVariables(variables)
        val jsonString = instance.toJson()

        val queryJsonHelper = jsonAdapter.fromJson(jsonString)
        assertEquals(queryJsonHelper?.query, queryString)
        assertEquals(queryJsonHelper?.variables, variables)
    }

    @Test
    fun withoutVariables() {
        val queryString = "query AppInfoQuery { product { appName appType }}"
        val instance = Query(queryString)
        val jsonString = instance.toJson()

        val queryJsonHelper = jsonAdapter.fromJson(jsonString)
        assertEquals(queryJsonHelper?.query, queryString)
        assertEquals(queryJsonHelper?.variables, mapOf<String, String>())
    }

    data class QueryTestHelper(
        val query: String,
        val variables: Map<String, String>
    )


}