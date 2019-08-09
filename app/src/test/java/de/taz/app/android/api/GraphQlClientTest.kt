package de.taz.app.android.api

import de.taz.app.android.api.dto.AppName
import de.taz.app.android.api.dto.AppType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class GraphQlClientTest {
    private val mockServer = MockWebServer()
    @Mock private lateinit var queryServiceMock: QueryService

    private lateinit var graphQlClient: GraphQlClient

    @Before
    fun setUp() {
        mockServer.start()
        MockitoAnnotations.initMocks(this)
        graphQlClient = GraphQlClient(
            httpClient = OkHttpClient(),
            url = mockServer.url("").toString(),
            queryService = queryServiceMock
        )
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun appInfoQuery() {
        doReturn(Query("query { product { appType appName }}"))
            .`when`(queryServiceMock).get(QueryType.AppInfoQuery)

        val mockResponse = MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"data\":{\"product\":{\"appType\":\"production\",\"appName\":\"taz\"}}}")
        mockServer.enqueue(mockResponse)

        runBlocking {
            val dataDto = graphQlClient.query(QueryType.AppInfoQuery)
            assertTrue(dataDto.authentificationToken == null)
            assertTrue(dataDto.product!!.appName!! == AppName.taz)
            assertTrue(dataDto.product!!.appType!! == AppType.production)
        }
    }
}