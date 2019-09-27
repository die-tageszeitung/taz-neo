package de.taz.app.android.api

import de.taz.app.android.api.dto.AppName
import de.taz.app.android.api.dto.AppType
import de.taz.app.android.api.request.RequestService
import de.taz.app.android.api.request.QueryType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.MockitoAnnotations

class GraphQlClientTest {
    private val mockServer = MockWebServer()
    @Mock private lateinit var queryServiceMock: RequestService

    private lateinit var graphQlClient: GraphQlClient

    @Before
    fun setUp() {
        mockServer.start()
        MockitoAnnotations.initMocks(this)
        graphQlClient = GraphQlClient(
            httpClient = OkHttpClient(),
            url = mockServer.url("").toString(),
            requestService = queryServiceMock
        )
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun appInfoQuery() {
        doReturn(GraphQlRequest("\"query\":\"query { product { appType appName }}\""))
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