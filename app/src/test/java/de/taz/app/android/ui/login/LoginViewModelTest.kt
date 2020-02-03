package de.taz.app.android.ui.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthInfo
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.AuthTokenInfo
import de.taz.app.android.util.AuthHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import org.junit.Rule
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class LoginViewModelTest {

    @kotlinx.coroutines.ObsoleteCoroutinesApi
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private val username = "username"
    private val password = "password"

    private val authTokenInfo = AuthTokenInfo(
        "token",
        AuthInfo(
            AuthStatus.valid,
            "message"
        )
    )

    @Mock
    lateinit var apiService: ApiService
    @Mock
    lateinit var authHelper: AuthHelper

    private lateinit var loginViewModel: LoginViewModel

    @ObsoleteCoroutinesApi
    @ExperimentalCoroutinesApi
    @Before
    fun setUp() {
            Dispatchers.setMain(mainThreadSurrogate)
            MockitoAnnotations.initMocks(this)

            authHelper.authStatusLiveData = MutableLiveData()

            loginViewModel = LoginViewModel(apiService = apiService, authHelper = authHelper)

        runBlocking {
            Mockito.doReturn(authTokenInfo).`when`(apiService).authenticate(username, password)
        }
    }

    @After
    fun tearDown() {
    }

    @Test
    fun getStatus() {
        assertTrue(loginViewModel.status.value == LoginViewModelState.INITIAL)

        loginViewModel.status.value = LoginViewModelState.SUBSCRIPTION_MISSING

        assertTrue(loginViewModel.status.value == LoginViewModelState.SUBSCRIPTION_MISSING)
    }

    @Test
    fun getNoInternet() {
        assertTrue(loginViewModel.noInternet.value == false)

        loginViewModel.noInternet.value = true

        assertTrue(loginViewModel.noInternet.value == true)

    }

    @Test
    fun login() {
    }

    @Test
    fun getUsername() {
        loginViewModel.login(username, password)
        assertEquals(username, loginViewModel.getUsername())
    }

    @Test
    fun resetPassword() {
        loginViewModel.resetPassword()
        assertNull(loginViewModel.getPassword())
    }

    @Test
    fun getPassword() {
        loginViewModel.login(username, password)
        assertEquals(password, loginViewModel.getPassword())
    }
}