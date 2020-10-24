package de.taz.app.android.api

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import de.taz.app.android.CONNECTION_FAILURE_BACKOFF_TIME_MS
import de.taz.app.android.api.dto.DataDto
import de.taz.app.android.api.dto.WrapperDto
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.persistence.repository.AppInfoRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ServerConnectionHelper
import de.taz.app.android.singletons.ToastHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import java.net.ConnectException
import kotlin.Exception
import kotlin.math.pow

class ApiServiceTest {
    @Mock
    private lateinit var authHelper: AuthHelper 
    @Mock
    private lateinit var toastHelper: ToastHelper 
    @Mock
    private lateinit var firebaseHelper: FirebaseHelper 
    @Mock
    private lateinit var graphQlClient: GraphQlClient 
    @Mock
    private lateinit var appInfoRepository: AppInfoRepository 
    @Mock
    private lateinit var okHttpClent: OkHttpClient 

    private lateinit var apiService: ApiService
    private lateinit var serverConnectionHelper: ServerConnectionHelper

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        Dispatchers.setMain(Dispatchers.Unconfined)
        serverConnectionHelper = ServerConnectionHelper(
            toastHelper = toastHelper,
            graphQlClient = lazy { graphQlClient },
            appInfoRepository = appInfoRepository,
            okHttpClient = okHttpClent
        )

        apiService = ApiService(
            authHelper = authHelper,
            toastHelper = toastHelper,
            serverConnectionHelper = serverConnectionHelper,
            firebaseHelper = firebaseHelper,
            graphQlClient = graphQlClient
        )
    }


    @Test
    fun connectionErrorWillRetry() = runBlocking {
        val responseMock = mock(WrapperDto::class.java)
        `when`(responseMock.data).thenReturn(mock(DataDto::class.java))
        `when`(graphQlClient.query(QueryType.AppInfo))
            .thenThrow(ConnectException())
            .thenThrow(ConnectException())
            .thenReturn(responseMock)

        // If we throw three connection problems the back off time should double three times. Add another second for safety
        val waitTime = 2f.pow(2) * CONNECTION_FAILURE_BACKOFF_TIME_MS + 1000L

        Assert.assertTrue(serverConnectionHelper.isAPIServerReachable)
        val runQuery = launch {
            apiService.getDataDto("test", QueryType.AppInfo)
        }

        val verification = launch {
            delay(200)
            // Because of connectexception the connection helper should be set to unreachable
            Assert.assertFalse(serverConnectionHelper.isAPIServerReachable)
            delay(waitTime.toLong() / 2)
            // Should still be in unreachable state after half the wait time
            Assert.assertFalse(serverConnectionHelper.isAPIServerReachable)
            delay(waitTime.toLong() / 2)
            // by now it should've been recovered
            Assert.assertTrue(serverConnectionHelper.isAPIServerReachable)
        }

        runQuery.join()
        verification.join()
    }


    @Test
    fun `getDataDto with recoverable server error retries`() = runBlocking {
        val responseMock = mock(WrapperDto::class.java)
        `when`(responseMock.data).thenReturn(mock(DataDto::class.java))
        `when`(graphQlClient.query(QueryType.AppInfo))
            .thenThrow(GraphQlClient.GraphQlRecoverableServerException(null))
            .thenThrow(GraphQlClient.GraphQlRecoverableServerException(null))
            .thenReturn(responseMock)

        // If we throw three connection problems the back off time should double three times. Add another second for safety
        val waitTime = 2f.pow(2) * CONNECTION_FAILURE_BACKOFF_TIME_MS + 1000L

        Assert.assertTrue(serverConnectionHelper.isAPIServerReachable)
        val runQuery = launch {
            apiService.getDataDto("test", QueryType.AppInfo)
        }

        val verification = launch {
            delay(waitTime.toLong() / 2)
            // Because of connectexception the connection helper should be set to unreachable
            Assert.assertFalse(serverConnectionHelper.isAPIServerReachable)
            delay(waitTime.toLong() / 2)
            // by now it should've been recovered
            Assert.assertTrue(serverConnectionHelper.isAPIServerReachable)
        }

        runQuery.join()
        verification.join()
    }

    @Test(expected = ConnectivityException.ImplementationException::class)
    fun `getDataDto with missing data will throw exception`(): Unit = runBlocking {
        `when`(graphQlClient.query(QueryType.AppInfo))
            .thenThrow(GraphQlClient.GraphQlImplementationException(null))

        Assert.assertTrue(serverConnectionHelper.isAPIServerReachable)

        apiService.getDataDto("test", QueryType.AppInfo)
    }

    @Test
    fun `getDataDto if query throws any unknown exception it will transform to api exception`(): Unit =
        runBlocking {
            `when`(graphQlClient.query(QueryType.AppInfo))
                .thenThrow(Exception())

            Assert.assertTrue(serverConnectionHelper.isAPIServerReachable)
            Assert.assertThrows(ConnectivityException.ImplementationException::class.java) {
                runBlocking { apiService.getDataDto("test", QueryType.AppInfo) }
            }

            // do not alter reachability
            Assert.assertTrue(serverConnectionHelper.isAPIServerReachable)
        }
}