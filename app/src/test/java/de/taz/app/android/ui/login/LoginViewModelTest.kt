package de.taz.app.android.ui.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.nhaarman.mockitokotlin2.doReturn
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthInfo
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.AuthTokenInfo
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import org.junit.Rule
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class LoginViewModelTest {

    @kotlinx.coroutines.ObsoleteCoroutinesApi
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private val username = "username"
    private val password = "password"

    private val message = "message"
    private val token = "token"

    private val subscriptionId = 161
    private val subscriptionPassword = "afa"

    private val validAuthInfo = AuthInfo(AuthStatus.valid, message)
    private val validAuthTokenInfo = AuthTokenInfo(token, validAuthInfo)

    private val elapsedAuthInfo = AuthInfo(AuthStatus.elapsed, message)
    private val elapsedAuthTokenInfo = AuthTokenInfo(token, elapsedAuthInfo)

    private val invalidAuthInfo = AuthInfo(AuthStatus.notValid, message)
    private val invalidAuthTokenInfo = AuthTokenInfo(token, invalidAuthInfo)

    private val idNotLinkedAuthInfo = AuthInfo(AuthStatus.tazIdNotLinked, message)
    private val idNotLinkedAuthTokenInfo = AuthTokenInfo(token, idNotLinkedAuthInfo)

    private val alreadyLinkedAuthInfo = AuthInfo(AuthStatus.alreadyLinked, message)
    private val alreadyLinkedAuthTokenInfo = AuthTokenInfo(token, alreadyLinkedAuthInfo)

    @Mock
    lateinit var apiService: ApiService
    @Mock
    lateinit var authHelper: AuthHelper
    @Mock
    lateinit var toastHelper: ToastHelper

    private lateinit var loginViewModel: LoginViewModel

    @ObsoleteCoroutinesApi
    @ExperimentalCoroutinesApi
    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        MockitoAnnotations.initMocks(this)

        authHelper.authStatusLiveData = MutableLiveData()

        loginViewModel = LoginViewModel(
            apiService = apiService,
            authHelper = authHelper,
            toastHelper = toastHelper
        )
    }

    @After
    fun tearDown() {
    }

    @Test
    fun initialStatus() {
        loginViewModel.status.value
        assertTrue(loginViewModel.status.value == LoginViewModelState.INITIAL)
    }

    @Test
    fun getNoInternet() {
        assertTrue(loginViewModel.noInternet.value == false)
        loginViewModel.noInternet.value = true
        assertTrue(loginViewModel.noInternet.value == true)
    }

    @Test
    fun loginWithoutUsername() = runBlocking {
        loginViewModel.login(null, password)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.USERNAME_MISSING)
    }

    @Test
    fun loginWithEmptyUsername() = runBlocking {
        loginViewModel.login("", password)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.USERNAME_MISSING)
    }

    @Test
    fun loginCredentialsWithEmptyPassword() = runBlocking {
        loginViewModel.login(username, "")?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_MISSING)
    }

    @Test
    fun loginCredentialsWithoutPassword() = runBlocking {
        loginViewModel.login(username, null)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_MISSING)
    }

    @Test
    fun loginSubscriptionWithEmptyPassword() = runBlocking {
        loginViewModel.login(subscriptionId.toString(), "")?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_MISSING)
    }

    @Test
    fun loginSubscriptionWithoutPassword() = runBlocking {
        loginViewModel.login(subscriptionId.toString(), null)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_MISSING)
    }

    @Test
    fun loginCredentialsSuccessful() = runBlocking {
        doReturn(validAuthTokenInfo).`when`(apiService).authenticate(username, password)
        loginViewModel.login(username, password)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.DONE)
    }

    @Test
    fun loginCredentialsElapsed() = runBlocking {
        doReturn(elapsedAuthTokenInfo).`when`(apiService).authenticate(username, password)
        loginViewModel.login(username, password)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.SUBSCRIPTION_ELAPSED)
    }

    @Test
    fun loginCredentialsNotValid() = runBlocking {
        doReturn(invalidAuthTokenInfo).`when`(apiService).authenticate(username, password)
        loginViewModel.login(username, password)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.CREDENTIALS_INVALID)
    }

    @Test
    fun loginCredentialsNoSubscriptionId() = runBlocking {
        doReturn(idNotLinkedAuthTokenInfo).`when`(apiService).authenticate(username, password)
        loginViewModel.login(username, password)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.SUBSCRIPTION_MISSING)
    }

    @Test
    fun loginCredentialsAlreadyLinked() = runBlocking {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Test
    fun loginSubscriptionValid() = runBlocking {
        doReturn(validAuthInfo).`when`(apiService).checkSubscriptionId(
            subscriptionId,
            subscriptionPassword
        )
        loginViewModel.login(subscriptionId.toString(), subscriptionPassword)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.CREDENTIALS_MISSING)
    }

    @Test
    fun loginSubscriptionElapsed() = runBlocking {
        doReturn(elapsedAuthInfo).`when`(apiService).checkSubscriptionId(
            subscriptionId,
            subscriptionPassword
        )
        loginViewModel.login(subscriptionId.toString(), subscriptionPassword)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.SUBSCRIPTION_ELAPSED)
    }

    @Test
    fun loginSubscriptionNotValid() = runBlocking {
        doReturn(invalidAuthInfo).`when`(apiService).checkSubscriptionId(
            subscriptionId,
            subscriptionPassword
        )
        loginViewModel.login(subscriptionId.toString(), subscriptionPassword)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.SUBSCRIPTION_INVALID)
    }

    @Test
    fun loginSubscriptionNotLinked() = runBlocking {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Test
    fun loginSubscriptionAlreadyLinked() = runBlocking {
        doReturn(alreadyLinkedAuthInfo).`when`(apiService).checkSubscriptionId(
            subscriptionId,
            subscriptionPassword
        )
        loginViewModel.login(subscriptionId.toString(), subscriptionPassword)?.join()
        assertTrue(loginViewModel.username == message)
        assertTrue(loginViewModel.status.value == LoginViewModelState.INITIAL)
    }


}