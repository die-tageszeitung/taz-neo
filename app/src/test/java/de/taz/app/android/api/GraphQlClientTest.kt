package de.taz.app.android.api

import de.taz.app.android.api.dto.AppName
import de.taz.app.android.api.dto.AppType
import de.taz.app.android.singletons.AuthHelper
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
    @Mock private lateinit var queryServiceMock: QueryService
    @Mock private lateinit var authHelper: AuthHelper

    private lateinit var graphQlClient: GraphQlClient

    @Before
    fun setUp() {
        mockServer.start()
        MockitoAnnotations.initMocks(this)
        graphQlClient = GraphQlClient(
            okHttpClient = OkHttpClient(),
            url = mockServer.url("").toString(),
            queryService= queryServiceMock,
            authHelper = authHelper
        )
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun appInfoQuery() {
        doReturn(Query("\"query\":\"query { product { appType appName }}\""))
            .`when`(queryServiceMock).get(QueryType.AppInfo)

        doReturn("").`when`(authHelper).token

        val mockResponse = MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"data\":{\"product\":{\"appType\":\"production\",\"appName\":\"taz\"}}}")
        mockServer.enqueue(mockResponse)

        runBlocking {
            val dataDto = graphQlClient.query(QueryType.AppInfo)
            assertTrue(dataDto?.data?.authentificationToken == null)
            assertTrue(dataDto?.data?.product!!.appName!! == AppName.taz)
            assertTrue(dataDto.data?.product!!.appType!! == AppType.production)
        }
    }
}