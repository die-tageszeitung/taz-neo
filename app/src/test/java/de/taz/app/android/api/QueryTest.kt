package de.taz.app.android.api

import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.variables.AuthenticationVariables
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryTest {

    private val jsonAdapter = JsonHelper.adapter<QueryTestHelper>()

    @Test
    fun withVariables() {
        val queryString = "query AppInfoQuery { product { appName }}"
        val instance = Query(queryString)
        val variables = AuthenticationVariables("user", "pass", DeviceFormat.mobile)
        instance.variables = variables
        val jsonString = instance.toJson()

        val queryJsonHelper = jsonAdapter.fromJson(jsonString)
        assertEquals(queryJsonHelper?.query, queryString)
        assertTrue(queryJsonHelper?.variables?.equalsAuth(variables) ?: false)
    }

    @Test
    fun withoutVariables() {
        val queryString = "query AppInfoQuery { product { appName appType }}"
        val instance = Query(queryString)
        val jsonString = instance.toJson()

        val queryJsonHelper = jsonAdapter.fromJson(jsonString)
        assertEquals(queryJsonHelper?.query, queryString)
        assertEquals(queryJsonHelper?.variables, TestAuthenticationVariables(null, null, null))
    }

    data class QueryTestHelper(
        val query: String,
        val variables: TestAuthenticationVariables
    )

    data class TestAuthenticationVariables(val user: String?, val password: String?, val deviceFormat: DeviceFormat?) {
        fun equalsAuth(authenticationVariables: AuthenticationVariables): Boolean {
            return this.user == authenticationVariables.user &&
                    this.password == authenticationVariables.password
        }
    }

}