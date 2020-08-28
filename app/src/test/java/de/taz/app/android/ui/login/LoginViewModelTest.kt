package de.taz.app.android.ui.login

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.*
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.FeedHelper
import de.taz.app.android.singletons.ToastHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import org.junit.Rule
import org.mockito.Mock
import org.mockito.Mockito.*
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

    private val email = "test@example.com"

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

    private val viewModelState = LoginViewModelState.CREDENTIALS_MISSING_REGISTER_FAILED

    private val invalidMailSubscriptionInfo = SubscriptionInfo(SubscriptionStatus.invalidMail)
    private val noPollEntrySubscriptionInfo = SubscriptionInfo(SubscriptionStatus.noPollEntry)
    private val subscriptionIdNotValidSubscriptionInfo =
        SubscriptionInfo(SubscriptionStatus.subscriptionIdNotValid)
    private val tazIdNotValidSubscriptionInfo = SubscriptionInfo(SubscriptionStatus.tazIdNotValid)
    private val waitForMailSubscriptionInfo = SubscriptionInfo(SubscriptionStatus.waitForMail)
    private val waitForProcSubscriptionInfo = SubscriptionInfo(SubscriptionStatus.waitForProc)
    private val validSubscriptionInfo = SubscriptionInfo(SubscriptionStatus.valid, token = token)
    private val alreadyLinkedSubscriptionInfo = SubscriptionInfo(SubscriptionStatus.alreadyLinked)
    private val elapsedSubscriptionInfo = SubscriptionInfo(SubscriptionStatus.elapsed)
    private val invalidConnectionSubscriptionInfo =
        SubscriptionInfo(SubscriptionStatus.invalidConnection)

    private val subscriptionResetInfoOk = SubscriptionResetInfo(SubscriptionResetStatus.ok)
    private val subscriptionResetInfoInvalidId =
        SubscriptionResetInfo(SubscriptionResetStatus.invalidSubscriptionId)
    private val subscriptionResetInfoNoMail = SubscriptionResetInfo(SubscriptionResetStatus.noMail)
    private val subscriptionResetInfoInvalidConnection =
        SubscriptionResetInfo(SubscriptionResetStatus.invalidConnection, email)

    @Mock
    lateinit var apiService: ApiService
    @Mock
    lateinit var authHelper: AuthHelper
    @Mock
    lateinit var toastHelper: ToastHelper
    @Mock
    lateinit var application: Application
    @Mock
    lateinit var feedHelper: FeedHelper

    private lateinit var loginViewModel: LoginViewModel

    @ObsoleteCoroutinesApi
    @ExperimentalCoroutinesApi
    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        MockitoAnnotations.initMocks(this)

        authHelper.authStatusLiveData = MutableLiveData()

        loginViewModel = LoginViewModel(
            application = application,
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
    fun loginWithEmtpyUsernameAndPassword() = runBlocking {
        loginViewModel.login(null, null)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.USERNAME_MISSING)
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
        assertTrue(loginViewModel.status.value == LoginViewModelState.CREDENTIALS_MISSING_REGISTER)
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

    @Test
    fun loginNull() = runBlocking {
        doReturn(null).`when`(apiService).checkSubscriptionId(
            subscriptionId,
            subscriptionPassword
        )
        loginViewModel.login(subscriptionId.toString(), subscriptionPassword)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.INITIAL)
    }

    @Test
    fun requestSubscription() = runBlocking {
        loginViewModel.requestSubscription(username)
        assertTrue(loginViewModel.username == username)
        assertTrue(loginViewModel.status.value == LoginViewModelState.SUBSCRIPTION_REQUEST)
    }

    @Test
    fun registerSuccessful() = runBlocking {
        doReturn(validSubscriptionInfo).`when`(apiService).trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(viewModelState)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.REGISTRATION_SUCCESSFUL)
    }

    @Test
    fun registerAlreadyLinked() = runBlocking {
        doReturn(alreadyLinkedSubscriptionInfo).`when`(apiService)
            .trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(viewModelState)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.EMAIL_ALREADY_LINKED)
    }

    @Test
    fun registerElapsed() = runBlocking {
        doReturn(elapsedSubscriptionInfo).`when`(apiService).trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(viewModelState)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.SUBSCRIPTION_ELAPSED)
    }

    @Test
    fun registerInvalidConnection() = runBlocking {
        doReturn(invalidConnectionSubscriptionInfo).`when`(apiService)
            .trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(viewModelState)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.SUBSCRIPTION_TAKEN)
    }

    @Test
    fun registerInvalidMail() = runBlocking {
        doReturn(invalidMailSubscriptionInfo).`when`(apiService)
            .trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(viewModelState)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.CREDENTIALS_MISSING_REGISTER_FAILED)
    }

    @Test
    fun registerNoPollEntry() = runBlocking {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Test
    fun registerSubscriptionIdNotValid() = runBlocking {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Test
    fun registerTazIdNotValid() = runBlocking {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Test
    fun registerWaitForMail() = runBlocking {
        doReturn(waitForMailSubscriptionInfo).`when`(apiService)
            .trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(viewModelState)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.REGISTRATION_EMAIL)
    }

    @Test
    fun registerWaitForProc() = runBlocking {
        doReturn(waitForProcSubscriptionInfo).`when`(apiService)
            .trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(viewModelState)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.LOADING)
        // polling logic tested separately
    }

    @Test
    fun registerNull() = runBlocking {
        val status = loginViewModel.status.value
        doReturn(null).`when`(apiService).trialSubscription(username, password)
        loginViewModel.register(viewModelState)?.join()
        assertTrue(loginViewModel.status.value == status)
    }

    @Test
    fun registerNoInternet() = runBlocking {
        val status = loginViewModel.status.value
        doThrow(ApiService.ApiServiceException.NoInternetException()).`when`(apiService)
            .trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(viewModelState)?.join()
        assertTrue(loginViewModel.status.value == status)
        assertTrue(loginViewModel.noInternet.value == true)
    }

    @Test
    fun pollInvalidMail() = runBlocking {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Test
    fun pollNoPollEntry() = runBlocking {
        doReturn(noPollEntrySubscriptionInfo).`when`(apiService).subscriptionPoll()
        loginViewModel.poll(0).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.POLLING_FAILED)
    }

    @Test
    fun pollSubscriptionIdNotValid() = runBlocking {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Test
    fun pollTazIdNotValid() = runBlocking {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Test
    fun pollWaitForMail() = runBlocking {
        doReturn(waitForMailSubscriptionInfo).`when`(apiService).subscriptionPoll()
        loginViewModel.poll(0).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.REGISTRATION_EMAIL)
    }

    @Test
    fun pollWaitForProc() = runBlocking {
        doReturn(waitForProcSubscriptionInfo).doReturn(waitForProcSubscriptionInfo)
            .doReturn(waitForMailSubscriptionInfo).`when`(apiService).subscriptionPoll()
        loginViewModel.poll(0, runBlocking = true).join()

        verify(apiService, times(3)).subscriptionPoll()
        assertTrue(loginViewModel.status.value == LoginViewModelState.REGISTRATION_EMAIL)
    }

    @Test
    fun pollNull() = runBlocking {
        doReturn(null).doReturn(waitForMailSubscriptionInfo).`when`(apiService).subscriptionPoll()
        loginViewModel.poll(0, runBlocking = true).join()
        verify(apiService, times(2)).subscriptionPoll()
        assertTrue(loginViewModel.status.value == LoginViewModelState.REGISTRATION_EMAIL)
    }

    @Test
    fun pollNoInternet() = runBlocking {
        doThrow(ApiService.ApiServiceException.NoInternetException()).doReturn(
            waitForMailSubscriptionInfo
        ).`when`(apiService).subscriptionPoll()
        loginViewModel.poll(0, runBlocking = true).join()
        verify(apiService, times(2)).subscriptionPoll()
        assertTrue(loginViewModel.status.value == LoginViewModelState.REGISTRATION_EMAIL)
        assertTrue(loginViewModel.noInternet.value == true)
    }

    @Test
    fun requestCredentialsPasswordResetEmptyEmail() {
        loginViewModel.requestCredentialsPasswordReset("")
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST)
    }

    @Test
    fun requestCredentialsPasswordResetOk() = runBlocking {
        doReturn(PasswordResetInfo.ok).`when`(apiService).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST_DONE)
    }

    @Test
    fun requestCredentialsPasswordResetError() = runBlocking {
        doReturn(PasswordResetInfo.error).`when`(apiService).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST)
    }

    @Test
    fun requestCredentialsPasswordResetInvalidMail() = runBlocking {
        doReturn(PasswordResetInfo.invalidMail).`when`(apiService)
            .requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST_INVALID_MAIL)
    }

    @Test
    fun requestCredentialsPasswordResetMailError() = runBlocking {
        doReturn(PasswordResetInfo.mailError).`when`(apiService)
            .requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST)
    }

    @Test
    fun requestCredentialsPasswordResetNull() = runBlocking {
        doReturn(null).`when`(apiService).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST)
    }

    @Test
    fun requestSubscriptionPasswordOk() = runBlocking {
        doReturn(subscriptionResetInfoOk).`when`(apiService)
            .requestSubscriptionPassword(subscriptionId)
        loginViewModel.requestSubscriptionPassword(subscriptionId).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST_DONE)
    }

    @Test
    fun requestSubscriptionPasswordInvalidId() = runBlocking {
        doReturn(subscriptionResetInfoInvalidId).`when`(apiService)
            .requestSubscriptionPassword(subscriptionId)
        loginViewModel.requestSubscriptionPassword(subscriptionId).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST_INVALID_ID)
    }

    @Test
    fun requestSubscriptionPasswordNoMail() = runBlocking {
        doReturn(subscriptionResetInfoNoMail).`when`(apiService)
            .requestSubscriptionPassword(subscriptionId)
        loginViewModel.requestSubscriptionPassword(subscriptionId).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST_NO_MAIL)
    }

    @Test
    fun requestSubscriptionPasswordInvalidConnection() = runBlocking {
        doReturn(subscriptionResetInfoInvalidConnection).`when`(apiService)
            .requestSubscriptionPassword(subscriptionId)
        loginViewModel.requestSubscriptionPassword(subscriptionId).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.INITIAL)
        assertTrue(loginViewModel.username == email)
    }

    @Test
    fun requestSubscriptionPasswordNull() = runBlocking {
        doReturn(null).`when`(apiService).requestSubscriptionPassword(subscriptionId)
        loginViewModel.requestSubscriptionPassword(subscriptionId).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST)
    }

    @Test
    fun requestSubscriptionPasswordNoInternet() = runBlocking {
        doThrow(ApiService.ApiServiceException.NoInternetException()).`when`(apiService)
            .requestSubscriptionPassword(subscriptionId)
        loginViewModel.requestSubscriptionPassword(subscriptionId).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST)
        assertTrue(loginViewModel.noInternet.value == true)
    }

    @Test
    fun requestCredentialsPasswordOk() = runBlocking {
        doReturn(PasswordResetInfo.ok).`when`(apiService).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST_DONE)
    }

    @Test
    fun requestCredentialsPasswordMailError() = runBlocking {
        doReturn(PasswordResetInfo.mailError).`when`(apiService)
            .requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST)
    }

    @Test
    fun requestCredentialsPasswordInvalidMail() = runBlocking {
        doReturn(PasswordResetInfo.invalidMail).`when`(apiService)
            .requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST_INVALID_MAIL)
    }

    @Test
    fun requestCredentialsPasswordError() = runBlocking {
        doReturn(PasswordResetInfo.error).`when`(apiService).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        verify(toastHelper, times(1)).showToast(anyInt(), anyBoolean())
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST)
    }

    @Test
    fun requestCredentialsPasswordNull() = runBlocking {
        doReturn(null).`when`(apiService).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        verify(toastHelper, times(1)).showToast(anyInt(), anyBoolean())
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST)
    }

    @Test
    fun requestCredentialsPasswordNoInternet() = runBlocking {
        doThrow(ApiService.ApiServiceException.NoInternetException()).`when`(
            apiService
        ).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.PASSWORD_REQUEST)
        assertTrue(loginViewModel.noInternet.value == true)
    }

    @Test
    fun connectValid() = runBlocking {
        doReturn(validSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.connect(email, password, subscriptionId, subscriptionPassword).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.REGISTRATION_SUCCESSFUL)
    }

    @Test
    fun connectAlreadyLinked() = runBlocking {
        doReturn(alreadyLinkedSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.connect(email, password, subscriptionId, subscriptionPassword).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.EMAIL_ALREADY_LINKED)
    }

    @Test
    fun connectElapsed() = runBlocking {
        doReturn(elapsedSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.connect(email, password, subscriptionId, subscriptionPassword).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.SUBSCRIPTION_ELAPSED)
    }

    @Test
    fun connectInvalidConnection() = runBlocking {
        doReturn(invalidConnectionSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.connect(email, password, subscriptionId, subscriptionPassword).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.SUBSCRIPTION_TAKEN)
    }

    @Test
    fun connectInvalidMail() = runBlocking {
        doReturn(invalidMailSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.status.postValue(LoginViewModelState.CREDENTIALS_MISSING_LOGIN)
        loginViewModel.connect(email, password, subscriptionId, subscriptionPassword).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.CREDENTIALS_MISSING_LOGIN_FAILED)
    }

    @Test
    fun connectNewInvalidMail() = runBlocking {
        doReturn(invalidMailSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)

        loginViewModel.status.postValue(LoginViewModelState.CREDENTIALS_MISSING_REGISTER)
        loginViewModel.connect(email, password, subscriptionId, subscriptionPassword).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.CREDENTIALS_MISSING_REGISTER_FAILED)
    }

    @Test
    fun connectInvalidTazID() = runBlocking {
        doReturn(tazIdNotValidSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.status.postValue(LoginViewModelState.CREDENTIALS_MISSING_LOGIN)
        loginViewModel.connect(email, password, subscriptionId, subscriptionPassword).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.CREDENTIALS_MISSING_LOGIN_FAILED)
    }

    @Test
    fun connectNewInvalidTazID() = runBlocking {
        doReturn(tazIdNotValidSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)

        loginViewModel.status.postValue(LoginViewModelState.CREDENTIALS_MISSING_REGISTER)
        loginViewModel.connect(email, password, subscriptionId, subscriptionPassword).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.CREDENTIALS_MISSING_REGISTER_FAILED)
    }

    @Test
    fun connectNoPoll() = runBlocking {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Test
    fun connectSubscriptionIdInvalid() = runBlocking {
        doReturn(subscriptionIdNotValidSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.connect(email, password, subscriptionId, subscriptionPassword).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.SUBSCRIPTION_MISSING_INVALID_ID)
    }

    @Test
    fun connectWaitForMail() = runBlocking {
        doReturn(waitForMailSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.connect(email, password, subscriptionId, subscriptionPassword).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.REGISTRATION_EMAIL)
    }

    @Test
    fun connectWaitForProc() = runBlocking {
        doReturn(waitForProcSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.connect(email, password, subscriptionId, subscriptionPassword).join()
        assertTrue(loginViewModel.status.value == LoginViewModelState.LOADING)
    }

    @Test
    fun connectNull() = runBlocking {
        val status = loginViewModel.status.value
        doReturn(null).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.connect(email, password, subscriptionId, subscriptionPassword).join()
        assertTrue(loginViewModel.status.value == status)
    }

    @Test
    fun connectNoInternet() = runBlocking {
        val status = loginViewModel.status.value
        doThrow(ApiService.ApiServiceException.NoInternetException()).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.connect(email, password, subscriptionId, subscriptionPassword).join()
        assertTrue(loginViewModel.status.value == status)
        assertTrue(loginViewModel.noInternet.value == true)
    }

}