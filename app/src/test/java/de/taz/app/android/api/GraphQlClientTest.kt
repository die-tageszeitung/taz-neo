package de.taz.app.android.api

import de.taz.app.android.GRAPHQL_ENDPOINT
import de.taz.app.android.api.dto.AppName
import de.taz.app.android.api.dto.AppType
import de.taz.app.android.singletons.AuthHelper
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.MockitoAnnotations

class GraphQlClientTest {
    @Mock
    private lateinit var queryServiceMock: QueryService
    @Mock
    private lateinit var authHelper: AuthHelper

    private lateinit var graphQlClient: GraphQlClient

    private val mockClient = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                if (request.url.toString() == GRAPHQL_ENDPOINT) {
                    val responseHeaders = headersOf("Content-Type" to listOf("application/json"))
                    respond("{\"data\":{\"product\":{\"appType\":\"production\",\"appName\":\"taz\"}}}", headers = responseHeaders)

                } else {
                    throw IllegalStateException("This mock client does not handle ${request.url}")

                }
            }
        }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        graphQlClient = GraphQlClient(
            mockClient,
            GRAPHQL_ENDPOINT,
            queryService = queryServiceMock,
            authHelper = authHelper
        )
    }

    @Test
    fun appInfoQuery() {
        doReturn(Query("\"query\":\"query { product { appType appName }}\""))
            .`when`(queryServiceMock).get(QueryType.AppInfo)

        doReturn("").`when`(authHelper).token

        runBlocking {
            val dataDto = graphQlClient.query(QueryType.AppInfo)
            assertTrue(dataDto.data?.authentificationToken == null)
            assertTrue(dataDto.data?.product!!.appName!! == AppName.taz)
            assertTrue(dataDto.data?.product!!.appType!! == AppType.production)
        }
    }
}