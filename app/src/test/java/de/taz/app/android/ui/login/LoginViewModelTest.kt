package de.taz.app.android.ui.login

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.*
import de.taz.app.android.content.FeedService
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.SubscriptionPollHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.NoOpTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import java.io.File
import java.net.ConnectException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class LoginViewModelTest {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    /*
     * TODO (jdillmann): These tests should actually replace all Dispatchers with the StandardTestDispatcher
     *   see https://www.youtube.com/watch?v=hzTU0lh-TIw for a good tutorial
     *   Unfortunately that does not work with infinite recursions as happening on handlePoll()
     *   Thus we simply disable the flaky poll tests for now.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())
    */

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

    private val viewModelState = LoginViewModelState.CREDENTIALS_MISSING_FAILED

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
        SubscriptionResetInfo(SubscriptionResetStatus.invalidConnection)

    @Mock
    lateinit var apiService: ApiService

    @Mock
    lateinit var authHelper: AuthHelper

    @Mock
    lateinit var dataStore: DataStore<Preferences>

    @Mock
    lateinit var application: Application

    @Mock
    lateinit var toastHelper: ToastHelper

    @Mock
    lateinit var feedService: FeedService

    @Mock
    lateinit var subscriptionPollHelper: SubscriptionPollHelper

    private lateinit var loginViewModel: LoginViewModel

    @ObsoleteCoroutinesApi
    @ExperimentalCoroutinesApi
    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create {
            File.createTempFile("test", ".preferences_pb", null)
        }
        authHelper = AuthHelper(application, dataStore)

        loginViewModel = LoginViewModel(
            application = application,
            apiService = apiService,
            authHelper = authHelper,
            toastHelper = toastHelper,
            feedService = feedService,
            subscriptionPollHelper = subscriptionPollHelper,
            tracker = NoOpTracker()
        ).apply { status.postValue(LoginViewModelState.INITIAL) }
    }

    @After
    fun tearDown() {
    }

    @Test
    fun getNoInternet() {
        assertTrue(loginViewModel.noInternet.value == false)
        loginViewModel.noInternet.value = true
        assertTrue(loginViewModel.noInternet.value == true)
    }

    @Test
    fun loginWithoutUsername() = runTest {
        loginViewModel.login(null, password)
        assertEquals(LoginViewModelState.USERNAME_MISSING, loginViewModel.status.value)
    }

    @Test
    fun loginWithEmptyUsername() = runTest {
        loginViewModel.login("", password)
        assertEquals(LoginViewModelState.USERNAME_MISSING, loginViewModel.status.value)
    }

    @Test
    fun loginCredentialsWithEmptyPassword() = runTest {
        loginViewModel.login(username, "")
        assertEquals(LoginViewModelState.PASSWORD_MISSING, loginViewModel.status.value)
    }

    @Test
    fun loginCredentialsWithoutPassword() = runTest {
        loginViewModel.login(username, null)
        assertEquals(LoginViewModelState.PASSWORD_MISSING, loginViewModel.status.value)
    }

    @Test
    fun loginWithEmtpyUsernameAndPassword() = runTest {
        loginViewModel.login(null, null)
        assertEquals(LoginViewModelState.USERNAME_MISSING, loginViewModel.status.value)
    }

    @Test
    fun loginSubscriptionWithEmptyPassword() = runTest {
        loginViewModel.login(subscriptionId.toString(), "")
        assertEquals(LoginViewModelState.PASSWORD_MISSING, loginViewModel.status.value)
    }

    @Test
    fun loginSubscriptionWithoutPassword() = runTest {
        loginViewModel.login(subscriptionId.toString(), null)
        assertEquals(LoginViewModelState.PASSWORD_MISSING, loginViewModel.status.value)
    }

    @Test
    fun loginCredentialsSuccessful() = runTest {
        doReturn(validAuthTokenInfo).`when`(apiService).authenticate(username, password)
        loginViewModel.login(username, password)?.join()
        assertEquals(LoginViewModelState.DONE, loginViewModel.status.value)
    }

    @Test
    fun loginCredentialsElapsed() = runTest {
        doReturn(elapsedAuthTokenInfo).`when`(apiService).authenticate(username, password)
        loginViewModel.login(username, password)?.join()
        assertEquals(LoginViewModelState.SUBSCRIPTION_ELAPSED, loginViewModel.status.value)
    }

    @Test
    fun loginCredentialsNotValid() = runTest {
        doReturn(invalidAuthTokenInfo).`when`(apiService).authenticate(username, password)
        loginViewModel.login(username, password)?.join()
        assertEquals(LoginViewModelState.CREDENTIALS_INVALID, loginViewModel.status.value)
    }

    @Test
    fun loginCredentialsNoSubscriptionId() = runTest {
        doReturn(idNotLinkedAuthTokenInfo).`when`(apiService).authenticate(username, password)
        loginViewModel.login(username, password)?.join()
        assertEquals(LoginViewModelState.SUBSCRIPTION_MISSING, loginViewModel.status.value)
    }

    @Test
    fun loginCredentialsAlreadyLinked() = runTest {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    /* TODO readd those tests when checkSubscriptionId is used again
        @Test
        fun loginSubscriptionValid() = runTest {
            doReturn(validAuthInfo).`when`(apiService).checkSubscriptionId(
                subscriptionId,
                subscriptionPassword
            )
            loginViewModel.login(subscriptionId.toString(), subscriptionPassword)?.join()
            assertEquals(LoginViewModelState.CREDENTIALS_MISSING_REGISTER)
        }

        @Test
        fun loginSubscriptionElapsed() = runTest {
            doReturn(elapsedAuthInfo).`when`(apiService).checkSubscriptionId(
                subscriptionId,
                subscriptionPassword
            )
            loginViewModel.login(subscriptionId.toString(), subscriptionPassword)?.join()
            assertEquals(LoginViewModelState.SUBSCRIPTION_ELAPSED)
        }

        @Test
        fun loginSubscriptionNotValid() = runTest {
            doReturn(invalidAuthInfo).`when`(apiService).checkSubscriptionId(
                subscriptionId,
                subscriptionPassword
            )
            loginViewModel.login(subscriptionId.toString(), subscriptionPassword)?.join()
            assertEquals(LoginViewModelState.SUBSCRIPTION_INVALID)
        }

        @Test
        fun loginSubscriptionNotLinked() = runTest {
            // Do not test as this should not happen and therefore there exists no defined way to proceed
        }

        @Test
        fun loginSubscriptionAlreadyLinked() = runTest {
            doReturn(alreadyLinkedAuthInfo).`when`(apiService).checkSubscriptionId(
                subscriptionId,
                subscriptionPassword
            )
            loginViewModel.login(subscriptionId.toString(), subscriptionPassword)
            assertTrue(loginViewModel.username == message)
            assertEquals(LoginViewModelState.INITIAL)
        }
    */
    @Test
    fun loginNull() = runTest {
        doReturn(null).`when`(apiService).checkSubscriptionId(
            subscriptionId,
            subscriptionPassword
        )
        loginViewModel.login(subscriptionId.toString(), subscriptionPassword)?.join()
        assertEquals(LoginViewModelState.INITIAL, loginViewModel.status.value)
    }

    @Test
    fun requestSubscription() = runTest {
        loginViewModel.requestSubscription(username)
        assertTrue(loginViewModel.username == username)
        assertEquals(LoginViewModelState.SUBSCRIPTION_REQUEST, loginViewModel.status.value)
    }

    @Test
    fun registerSuccessful() = runTest {
        doReturn(validSubscriptionInfo).`when`(apiService).trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(LoginViewModelState.INITIAL, viewModelState)?.join()
        assertEquals(LoginViewModelState.REGISTRATION_SUCCESSFUL, loginViewModel.status.value)
    }

    @Test
    fun registerAlreadyLinked() = runTest {
        doReturn(alreadyLinkedSubscriptionInfo).`when`(apiService)
            .trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(LoginViewModelState.INITIAL, viewModelState)?.join()
        assertEquals(LoginViewModelState.EMAIL_ALREADY_LINKED, loginViewModel.status.value)
    }

    @Test
    fun registerElapsed() = runTest {
        doReturn(elapsedSubscriptionInfo).`when`(apiService).trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(LoginViewModelState.INITIAL, viewModelState)?.join()
        assertEquals(LoginViewModelState.SUBSCRIPTION_ELAPSED, loginViewModel.status.value)
    }

    @Test
    fun registerInvalidConnection() = runTest {
        doReturn(invalidConnectionSubscriptionInfo).`when`(apiService)
            .trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(LoginViewModelState.INITIAL, viewModelState)?.join()
        assertEquals(LoginViewModelState.SUBSCRIPTION_TAKEN, loginViewModel.status.value)
    }

    @Test
    fun registerInvalidMail() = runTest {
        doReturn(invalidMailSubscriptionInfo).`when`(apiService)
            .trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(LoginViewModelState.INITIAL, viewModelState)?.join()
        assertEquals(LoginViewModelState.CREDENTIALS_MISSING_FAILED, loginViewModel.status.value)
    }

    @Test
    fun registerNoPollEntry() = runTest {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Test
    fun registerSubscriptionIdNotValid() = runTest {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Test
    fun registerTazIdNotValid() = runTest {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Test
    fun registerWaitForMail() = runTest {
        doReturn(waitForMailSubscriptionInfo).`when`(apiService)
            .trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(LoginViewModelState.INITIAL, viewModelState)?.join()
        assertEquals(LoginViewModelState.REGISTRATION_EMAIL, loginViewModel.status.value)
    }

    @Test
    fun registerWaitForProc() = runTest {
        doReturn(waitForProcSubscriptionInfo).`when`(apiService)
            .trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(LoginViewModelState.INITIAL, viewModelState)?.join()
        assertEquals(LoginViewModelState.LOADING, loginViewModel.status.value)
        // polling logic tested separately
    }

    @Test
    fun registerNull() = runTest {
        val status = loginViewModel.status.value
        loginViewModel.register(LoginViewModelState.INITIAL, viewModelState)?.join()
        assertEquals(status, loginViewModel.status.value)
    }

    @Test
    fun registerNoInternet() = runTest {
        val status = LoginViewModelState.INITIAL
        doThrow(ConnectivityException.NoInternetException(cause = ConnectException())).`when`(
            apiService
        )
            .trialSubscription(username, password)
        loginViewModel.username = username
        loginViewModel.password = password
        loginViewModel.register(LoginViewModelState.INITIAL, viewModelState)?.join()
        assertEquals(status, loginViewModel.status.value)
        assertTrue(loginViewModel.noInternet.value == true)
    }

    @Test
    fun pollInvalidMail() = runTest {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Ignore("Flaky due to coroutine concurrency")
    @Test
    fun pollNoPollEntry() = runTest {
        doReturn(noPollEntrySubscriptionInfo).`when`(apiService).subscriptionPoll()
        loginViewModel.poll(LoginViewModelState.INITIAL, 0)
        assertEquals(LoginViewModelState.POLLING_FAILED, loginViewModel.status.value)
    }

    @Ignore("Flaky due to coroutine concurrency")
    @Test
    fun pollSubscriptionIdNotValid() = runTest {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Ignore("Flaky due to coroutine concurrency")
    @Test
    fun pollTazIdNotValid() = runTest {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Ignore("Flaky due to coroutine concurrency")
    @Test
    fun pollWaitForMail() = runTest {
        doReturn(waitForMailSubscriptionInfo).`when`(apiService).subscriptionPoll()
        loginViewModel.poll(LoginViewModelState.REGISTRATION_EMAIL, 0)
        assertEquals(LoginViewModelState.REGISTRATION_EMAIL, loginViewModel.status.value)
    }

    @Ignore("Flaky due to coroutine concurrency")
    @Test
    fun pollWaitForProc() = runTest {
        doReturn(waitForProcSubscriptionInfo).doReturn(waitForProcSubscriptionInfo)
            .doReturn(waitForMailSubscriptionInfo).`when`(apiService).subscriptionPoll()
        loginViewModel.poll(LoginViewModelState.REGISTRATION_EMAIL, 0)

        verify(apiService, times(3)).subscriptionPoll()
        assertEquals(LoginViewModelState.REGISTRATION_EMAIL, loginViewModel.status.value)
    }

    @Ignore("Flaky due to coroutine concurrency")
    @Test
    fun pollNull() = runTest {
        doReturn(null).doReturn(waitForMailSubscriptionInfo).`when`(apiService).subscriptionPoll()
        loginViewModel.poll(LoginViewModelState.REGISTRATION_EMAIL, 0)
        verify(apiService, times(2)).subscriptionPoll()
        assertEquals(LoginViewModelState.REGISTRATION_EMAIL, loginViewModel.status.value)
    }

    @Ignore("Flaky due to coroutine concurrency")
    @Test
    fun pollNoInternet() = runTest {
        doThrow(ConnectivityException.NoInternetException(cause = ConnectException())).doReturn(
            waitForMailSubscriptionInfo
        ).`when`(apiService).subscriptionPoll()
        loginViewModel.poll(LoginViewModelState.INITIAL, 0)
        verify(apiService, times(2)).subscriptionPoll()
        assertEquals(LoginViewModelState.REGISTRATION_EMAIL, loginViewModel.status.value)
        assertTrue(loginViewModel.noInternet.value == true)
    }

    @Test
    fun requestCredentialsPasswordResetEmptyEmail() {
        loginViewModel.requestCredentialsPasswordReset("")
        assertEquals(LoginViewModelState.PASSWORD_REQUEST, loginViewModel.status.value)
    }

    @Test
    fun requestCredentialsPasswordResetOk() = runTest {
        doReturn(PasswordResetInfo.ok).`when`(apiService).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST_DONE, loginViewModel.status.value)
    }

    @Test
    fun requestCredentialsPasswordResetError() = runTest {
        doReturn(PasswordResetInfo.error).`when`(apiService).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST, loginViewModel.status.value)
    }

    @Test
    fun requestCredentialsPasswordResetInvalidMail() = runTest {
        doReturn(PasswordResetInfo.invalidMail).`when`(apiService)
            .requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST_INVALID_MAIL, loginViewModel.status.value)
    }

    @Test
    fun requestCredentialsPasswordResetMailError() = runTest {
        doReturn(PasswordResetInfo.mailError).`when`(apiService)
            .requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST, loginViewModel.status.value)
    }

    @Test
    fun requestCredentialsPasswordResetNull() = runTest {
        doReturn(null).`when`(apiService).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST, loginViewModel.status.value)
    }

    @Test
    fun requestSubscriptionPasswordOk() = runTest {
        doReturn(subscriptionResetInfoOk).`when`(apiService)
            .requestSubscriptionPassword(subscriptionId)
        loginViewModel.requestSubscriptionPassword(subscriptionId).join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST_DONE, loginViewModel.status.value)
    }

    @Test
    fun requestSubscriptionPasswordInvalidId() = runTest {
        doReturn(subscriptionResetInfoInvalidId).`when`(apiService)
            .requestSubscriptionPassword(subscriptionId)
        loginViewModel.requestSubscriptionPassword(subscriptionId).join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST_INVALID_ID, loginViewModel.status.value)
    }

    @Test
    fun requestSubscriptionPasswordNoMail() = runTest {
        doReturn(subscriptionResetInfoNoMail).`when`(apiService)
            .requestSubscriptionPassword(subscriptionId)
        loginViewModel.requestSubscriptionPassword(subscriptionId).join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST_NO_MAIL, loginViewModel.status.value)
    }

    @Test
    fun requestSubscriptionPasswordInvalidConnection() = runTest {
        doReturn(subscriptionResetInfoInvalidConnection).`when`(apiService)
            .requestSubscriptionPassword(subscriptionId)
        loginViewModel.requestSubscriptionPassword(subscriptionId).join()
        assertEquals(LoginViewModelState.INITIAL, loginViewModel.status.value)
    }

    @Test
    fun requestSubscriptionPasswordNull() = runTest {
        doReturn(null).`when`(apiService).requestSubscriptionPassword(subscriptionId)
        loginViewModel.requestSubscriptionPassword(subscriptionId).join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST, loginViewModel.status.value)
    }

    @Test
    fun requestSubscriptionPasswordNoInternet() = runTest {
        doThrow(ConnectivityException.NoInternetException(cause = ConnectException())).`when`(
            apiService
        )
            .requestSubscriptionPassword(subscriptionId)
        loginViewModel.requestSubscriptionPassword(subscriptionId).join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST, loginViewModel.status.value)
        assertTrue(loginViewModel.noInternet.value == true)
    }

    @Test
    fun requestCredentialsPasswordOk() = runTest {
        doReturn(PasswordResetInfo.ok).`when`(apiService).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST_DONE, loginViewModel.status.value)
    }

    @Test
    fun requestCredentialsPasswordMailError() = runTest {
        doReturn(PasswordResetInfo.mailError).`when`(apiService)
            .requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST, loginViewModel.status.value)
    }

    @Test
    fun requestCredentialsPasswordInvalidMail() = runTest {
        doReturn(PasswordResetInfo.invalidMail).`when`(apiService)
            .requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST_INVALID_MAIL, loginViewModel.status.value)
    }

    @Test
    fun requestCredentialsPasswordError() = runTest {
        doReturn(PasswordResetInfo.error).`when`(apiService).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST, loginViewModel.status.value)
    }

    @Test
    fun requestCredentialsPasswordNull() = runTest {
        doReturn(null).`when`(apiService).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST, loginViewModel.status.value)
    }

    @Test
    fun requestCredentialsPasswordNoInternet() = runTest {
        doThrow(ConnectivityException.NoInternetException(cause = ConnectException())).`when`(
            apiService
        ).requestCredentialsPasswordReset(email)
        loginViewModel.requestCredentialsPasswordReset(email)?.join()
        assertEquals(LoginViewModelState.PASSWORD_REQUEST, loginViewModel.status.value)
        assertTrue(loginViewModel.noInternet.value == true)
    }

    @Test
    fun connectValid() = runTest {
        doReturn(validSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.username = email
        loginViewModel.password = password
        loginViewModel.subscriptionId = subscriptionId
        loginViewModel.subscriptionPassword = subscriptionPassword
        loginViewModel.connect().join()
        assertEquals(LoginViewModelState.REGISTRATION_SUCCESSFUL, loginViewModel.status.value)
    }

    @Test
    fun connectAlreadyLinked() = runTest {
        doReturn(alreadyLinkedSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.username = email
        loginViewModel.password = password
        loginViewModel.subscriptionId = subscriptionId
        loginViewModel.subscriptionPassword = subscriptionPassword
        loginViewModel.connect().join()
        assertEquals(LoginViewModelState.EMAIL_ALREADY_LINKED, loginViewModel.status.value)
    }

    @Test
    fun connectElapsed() = runTest {
        doReturn(elapsedSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.username = email
        loginViewModel.password = password
        loginViewModel.subscriptionId = subscriptionId
        loginViewModel.subscriptionPassword = subscriptionPassword
        loginViewModel.connect().join()
        assertEquals(LoginViewModelState.SUBSCRIPTION_ELAPSED, loginViewModel.status.value)
    }

    @Test
    fun connectInvalidConnection() = runTest {
        doReturn(invalidConnectionSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.username = email
        loginViewModel.password = password
        loginViewModel.subscriptionId = subscriptionId
        loginViewModel.subscriptionPassword = subscriptionPassword
        loginViewModel.connect().join()
        assertEquals(LoginViewModelState.SUBSCRIPTION_TAKEN, loginViewModel.status.value)
    }

    @Test
    fun connectInvalidMail() = runTest {
        doReturn(invalidMailSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.status.postValue(LoginViewModelState.CREDENTIALS_MISSING_LOGIN)
        loginViewModel.username = email
        loginViewModel.password = password
        loginViewModel.subscriptionId = subscriptionId
        loginViewModel.subscriptionPassword = subscriptionPassword
        loginViewModel.connect().join()
        assertEquals(LoginViewModelState.CREDENTIALS_MISSING_FAILED, loginViewModel.status.value)
    }

    @Test
    fun connectNewInvalidMail() = runTest {
        doReturn(invalidMailSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)

        loginViewModel.status.postValue(LoginViewModelState.CREDENTIALS_MISSING_REGISTER)
        loginViewModel.username = email
        loginViewModel.password = password
        loginViewModel.subscriptionId = subscriptionId
        loginViewModel.subscriptionPassword = subscriptionPassword
        loginViewModel.connect().join()
        assertEquals(LoginViewModelState.CREDENTIALS_MISSING_FAILED, loginViewModel.status.value)
    }

    @Test
    fun connectInvalidTazID() = runTest {
        doReturn(tazIdNotValidSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.status.postValue(LoginViewModelState.CREDENTIALS_MISSING_LOGIN)
        loginViewModel.username = email
        loginViewModel.password = password
        loginViewModel.subscriptionId = subscriptionId
        loginViewModel.subscriptionPassword = subscriptionPassword
        loginViewModel.connect().join()
        assertEquals(LoginViewModelState.CREDENTIALS_MISSING_FAILED, loginViewModel.status.value)
    }

    @Test
    fun connectNewInvalidTazID() = runTest {
        doReturn(tazIdNotValidSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)

        loginViewModel.status.postValue(LoginViewModelState.CREDENTIALS_MISSING_REGISTER)
        loginViewModel.username = email
        loginViewModel.password = password
        loginViewModel.subscriptionId = subscriptionId
        loginViewModel.subscriptionPassword = subscriptionPassword
        loginViewModel.connect().join()
        assertEquals(LoginViewModelState.CREDENTIALS_MISSING_FAILED, loginViewModel.status.value)
    }

    @Test
    fun connectNoPoll() = runTest {
        // Do not test as this should not happen and therefore there exists no defined way to proceed
    }

    @Test
    fun connectSubscriptionIdInvalid() = runTest {
        doReturn(subscriptionIdNotValidSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.username = email
        loginViewModel.password = password
        loginViewModel.subscriptionId = subscriptionId
        loginViewModel.subscriptionPassword = subscriptionPassword
        loginViewModel.connect().join()
        assertEquals(LoginViewModelState.SUBSCRIPTION_MISSING_INVALID_ID, loginViewModel.status.value)
    }

    @Test
    fun connectWaitForMail() = runTest {
        doReturn(waitForMailSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.username = email
        loginViewModel.password = password
        loginViewModel.subscriptionId = subscriptionId
        loginViewModel.subscriptionPassword = subscriptionPassword
        loginViewModel.connect().join()
        assertEquals(LoginViewModelState.REGISTRATION_EMAIL, loginViewModel.status.value)
    }

    @Test
    fun connectWaitForProc() = runTest {
        doReturn(waitForProcSubscriptionInfo).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.username = email
        loginViewModel.password = password
        loginViewModel.subscriptionId = subscriptionId
        loginViewModel.subscriptionPassword = subscriptionPassword
        loginViewModel.connect().join()
        assertEquals(LoginViewModelState.LOADING, loginViewModel.status.value)
    }

    @Test
    fun connectNull() = runTest {
        val status = loginViewModel.status.value
        doReturn(null).`when`(apiService)
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.username = email
        loginViewModel.password = password
        loginViewModel.subscriptionId = subscriptionId
        loginViewModel.subscriptionPassword = subscriptionPassword
        loginViewModel.connect().join()
        assertEquals(status, loginViewModel.status.value)
    }

    @Test
    fun connectNoInternet() = runTest {
        val status = loginViewModel.status.value
        doThrow(ConnectivityException.NoInternetException(cause = ConnectException())).`when`(
            apiService
        )
            .subscriptionId2TazId(email, password, subscriptionId, subscriptionPassword)
        loginViewModel.username = email
        loginViewModel.password = password
        loginViewModel.subscriptionId = subscriptionId
        loginViewModel.subscriptionPassword = subscriptionPassword
        loginViewModel.connect().join()
        assertEquals(status, loginViewModel.status.value)
        assertTrue(loginViewModel.noInternet.value == true)
    }

}