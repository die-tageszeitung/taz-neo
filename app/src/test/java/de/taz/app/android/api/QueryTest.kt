package de.taz.app.android.api

import de.taz.app.android.api.variables.AuthenticationVariables
import de.taz.app.android.api.variables.DeviceFormat
import de.taz.app.android.util.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueryTest {

    @Test
    fun withVariables() {
        val queryString = "query AppInfoQuery { product { appName }}"
        val instance = Query(queryString)
        val variables = AuthenticationVariables("user", "pass", DeviceFormat.mobile)
        instance.variables = variables
        val jsonString = Json.encodeToString(instance)

        val query = Json.decodeFromString<Query>(jsonString)
        assertEquals(query.query, queryString)
        assertEquals(query.variables, variables)
    }

    @Test
    fun withoutVariables() {
        val queryString = "query AppInfoQuery { product { appName appType }}"
        val instance = Query(queryString)
        val jsonString = Json.encodeToString(instance)

        val query: Query = Json.decodeFromString(jsonString)
        assertEquals(query.query, queryString)
        assertNull(query.variables)
    }
}