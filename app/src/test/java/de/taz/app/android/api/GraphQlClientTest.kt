package de.taz.app.android.api

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.AppNameDto
import de.taz.app.android.api.dto.AppTypeDto
import de.taz.app.android.api.models.AppName
import de.taz.app.android.api.models.AppType
import de.taz.app.android.api.dto.WrapperDto
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.util.Json
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.MockitoAnnotations
import java.io.File

class GraphQlClientTest {

    @kotlinx.coroutines.ObsoleteCoroutinesApi
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @Mock
    private lateinit var queryServiceMock: QueryService

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var dataStore: DataStore<Preferences>

    private lateinit var graphQlClient: GraphQlClient

    private val mockClient = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            json(Json)
            serialization(ContentType.Any, Json)
        }

        engine {
            addHandler { request ->
                if (request.url.toString() == BuildConfig.GRAPHQL_ENDPOINT_PREFIX + BuildConfig.GRAPHQL_ENDPOINT) {
                    val responseHeaders = headersOf("Content-Type" to listOf("application/json"))
                    respond(
                        "{\"data\":{\"product\":{\"appType\":\"production\",\"appName\":\"taz\"}}}",
                        headers = responseHeaders
                    )
                } else {
                    throw IllegalStateException("This mock client does not handle ${request.url}")
                }
            }
        }
    }

    @ExperimentalCoroutinesApi
    @ObsoleteCoroutinesApi
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(mainThreadSurrogate)

        dataStore = PreferenceDataStoreFactory.create {
            File.createTempFile("test", ".preferences_pb", null)
        }

        graphQlClient = GraphQlClient(
            mockClient,
            BuildConfig.GRAPHQL_ENDPOINT_PREFIX + BuildConfig.GRAPHQL_ENDPOINT,
            queryService = queryServiceMock,
            authHelper = AuthHelper(application, dataStore)
        )
    }

    @Test
    fun appInfoQuery() {
        runBlocking {
            doReturn(Query("\"query\":\"query { product { appType appName }}\""))
                .`when`(queryServiceMock).get(QueryType.AppInfo)

            val wrapperDto: WrapperDto = graphQlClient.query(QueryType.AppInfo)
            assertTrue(wrapperDto.data?.authentificationToken == null)
            assertTrue(wrapperDto.data?.product!!.appName!! == AppNameDto.taz)
            assertTrue(wrapperDto.data?.product!!.appType!! == AppTypeDto.production)
        }
    }
}