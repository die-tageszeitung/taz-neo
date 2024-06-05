package de.taz.app.android.ui.login

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.PasswordResetInfo
import de.taz.app.android.api.models.SubscriptionResetStatus
import de.taz.app.android.api.models.SubscriptionStatus
import de.taz.app.android.content.FeedService
import de.taz.app.android.monkey.getApplicationScope
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.SubscriptionPollHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

class LoginViewModel @JvmOverloads constructor(
    application: Application,
    private val apiService: ApiService = ApiService.getInstance(application),
    private val authHelper: AuthHelper = AuthHelper.getInstance(application),
    private val toastHelper: ToastHelper = ToastHelper.getInstance(application),
    private val feedService: FeedService = FeedService.getInstance(application),
    // even if IDE says it is unused - it will be initialized with the view model starting observing:
    private val subscriptionPollHelper: SubscriptionPollHelper = SubscriptionPollHelper.getInstance(
        application
    ),
    private val tracker: Tracker = Tracker.getInstance(application.applicationContext)
) : AndroidViewModel(application), CoroutineScope {

    private val log by Log

    private var statusBeforePasswordRequest: LoginViewModelState? = null
    var statusBeforeEmailAlreadyLinked: LoginViewModelState? = null


    // We can't use a MutableStateFlow because the implementation expects a non-distinct behavior
    // Thus we have to use a MutableSharedFlow and an extra _status to hold the current value for
    // access out of a coroutine.
    private val _statusFlow = MutableSharedFlow<LoginViewModelState>(replay = 1)
        .apply { tryEmit(LoginViewModelState.INITIAL) }
    private var _status: LoginViewModelState = LoginViewModelState.INITIAL
    val statusFlow = _statusFlow.asSharedFlow()
    var status: LoginViewModelState
        get() = _status
        set(value) {
            _status = value
            viewModelScope.launch { _statusFlow.emit(value) }
        }

    private val _noInternet = MutableStateFlow<Boolean>(false)
    val noInternetFlow = _noInternet.asStateFlow()
    var noInternet: Boolean
        get() = _noInternet.value
        set(value) {
            _noInternet.value = value
        }

    var username: String? = runBlocking { authHelper.email.get() }
    var backToSettingsAfterEmailSent = false
    val backToArticle: Boolean
        get() = articleName != null

    var password: String? = null
    var subscriptionId: Int? = null
    var subscriptionPassword: String? = null
    var backToHome: Boolean = false

    var firstName: String? = null
    var surName: String? = null

    var articleName: String? = null

    var createNewAccount: Boolean = true
    var validCredentials: Boolean = false

    var waitForMailSinceMs: Long = 0L

    fun setDone() {
        status = LoginViewModelState.DONE
    }

    suspend fun setPolling(startPolling: Boolean = true) {
        authHelper.email.set(username ?: "")
        authHelper.isPolling.set(startPolling)
    }

    fun backToMissingSubscription() {
        resetSubscriptionId()
        resetSubscriptionPassword()
        status = LoginViewModelState.SUBSCRIPTION_MISSING
    }

    fun login(initialUsername: String? = null, initialPassword: String? = null): Job? {
        val initialSubscriptionId = initialUsername?.toIntOrNull()
        // for a period of time the login should be possible with the subscription id
        // (the creation of the corresponding taz id is going to be refactored)
        val onlyLoginWithTazId = false
        return if (initialSubscriptionId != null && onlyLoginWithTazId) {
            subscriptionId = initialSubscriptionId
            subscriptionPassword = initialPassword

            status = LoginViewModelState.LOADING
            if (!initialPassword.isNullOrBlank()) {
                launch {
                    handleSubscriptionIdLogin(initialSubscriptionId, initialPassword)
                }
            } else {
                status = LoginViewModelState.PASSWORD_MISSING
                null
            }

        } else {
            initialUsername?.let { username = it }
            initialPassword?.let { password = it }

            status = LoginViewModelState.LOADING

            val tmpUsername = username ?: ""
            val tmpPassword = password

            if (tmpUsername.isBlank()) {
                status = LoginViewModelState.USERNAME_MISSING
                null
            } else if (tmpPassword.isNullOrBlank()) {
                status = LoginViewModelState.PASSWORD_MISSING
                null
            } else {
                launch { handleCredentialsLogin(tmpUsername, tmpPassword) }
            }
        }
    }

    private suspend fun handleSubscriptionIdLogin(
        subscriptionId: Int,
        subscriptionPassword: String
    ) {
        try {
            val subscriptionAuthInfo = apiService.checkSubscriptionId(
                subscriptionId,
                subscriptionPassword
            )

            when (subscriptionAuthInfo?.status) {
                AuthStatus.alreadyLinked -> {
                    username = subscriptionAuthInfo.message
                    status = LoginViewModelState.INITIAL
                    toastHelper.showToast(R.string.toast_login_with_email)
                }
                AuthStatus.tazIdNotLinked -> {
                    status = LoginViewModelState.CREDENTIALS_MISSING_REGISTER
                }
                AuthStatus.elapsed -> {
                    username?.let { authHelper.email.set(it) }
                    authHelper.status.set(AuthStatus.elapsed)
                    status = LoginViewModelState.SUBSCRIPTION_ELAPSED
                }
                AuthStatus.notValidMail,
                AuthStatus.notValid -> {
                    resetSubscriptionPassword()
                    status = LoginViewModelState.SUBSCRIPTION_INVALID
                }
                AuthStatus.valid ->
                    status = LoginViewModelState.CREDENTIALS_MISSING_REGISTER
                null -> {
                    status = LoginViewModelState.INITIAL
                    noInternet = true
                }
            }
        } catch (e: ConnectivityException) {
            status = LoginViewModelState.INITIAL
            noInternet = true
        }
    }

    private suspend fun handleCredentialsLogin(username: String, password: String) {
        try {
            val authTokenInfo = apiService.authenticate(username, password)
            val token = authTokenInfo?.token

            val isLoginWeek = authTokenInfo?.authInfo?.loginWeek ?: false
            authHelper.isLoginWeek.set(isLoginWeek)

            when (authTokenInfo?.authInfo?.status) {
                AuthStatus.valid -> {
                    authHelper.token.set(requireNotNull(token) { "valid login has token" })
                    authHelper.status.set(AuthStatus.valid)
                    authHelper.email.set(username)

                    if (isLoginWeek) {
                        log.debug("We got a login with 'loginWeek = true'. Refreshing feed")
                        getApplicationScope().launch {
                            try {
                                feedService.refreshFeed(BuildConfig.DISPLAYED_FEED)
                            } catch (e: ConnectivityException) {
                                log.error("Could not refresh the feed after wochentaz login", e)
                            }
                        }
                    }
                    status = LoginViewModelState.DONE
                }
                AuthStatus.notValid -> {
                    resetCredentialsPassword()
                    status = LoginViewModelState.CREDENTIALS_INVALID
                }
                AuthStatus.tazIdNotLinked ->
                    status = LoginViewModelState.SUBSCRIPTION_MISSING
                AuthStatus.elapsed -> {
                    authHelper.email.set(username)
                    token?.let { authHelper.token.set(it) }
                    authHelper.status.set(AuthStatus.elapsed)
                    status = LoginViewModelState.SUBSCRIPTION_ELAPSED
                }
                null -> {
                    status = LoginViewModelState.INITIAL
                    noInternet = true
                }
                else -> {
                    toastHelper.showSomethingWentWrongToast()
                    status = LoginViewModelState.INITIAL
                }
            }
        } catch (e: ConnectivityException.Recoverable) {
            status = LoginViewModelState.INITIAL
            noInternet = true
        }
    }

    fun requestSubscription(username: String? = null) {
        if (!username.isNullOrEmpty() && username.toIntOrNull() == null) {
            this.username = username
        }
        status = LoginViewModelState.SUBSCRIPTION_REQUEST
    }

    fun requestSwitchPrint2Digi() {
        status = LoginViewModelState.SWITCH_PRINT_2_DIGI_REQUEST
    }

    fun requestExtendPrintWithDigi() {
        status = LoginViewModelState.EXTEND_PRINT_WITH_DIGI_REQUEST
    }

    fun getTrialSubscriptionForExistingCredentials(previousState: LoginViewModelState) {
        register(previousState, LoginViewModelState.CREDENTIALS_MISSING_FAILED)
    }

    private fun getTrialSubscriptionForNewCredentials(previousState: LoginViewModelState) {
        register(previousState, LoginViewModelState.SUBSCRIPTION_REQUEST_INVALID_EMAIL)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun register(
        previousState: LoginViewModelState,
        invalidMailState: LoginViewModelState
    ): Job? {
        return runIfNotNull(this.username, this.password) { username1, password1 ->
            status = LoginViewModelState.LOADING
            launch {
                handleRegistration(
                    username1,
                    password1,
                    firstName,
                    surName,
                    invalidMailState,
                    previousState
                )
            }
        }
    }

    private suspend fun handleRegistration(
        username: String,
        password: String,
        firstName: String?,
        surname: String?,
        invalidMailState: LoginViewModelState,
        previousState: LoginViewModelState
    ) {
        try {
            val subscriptionInfo = apiService.trialSubscription(
                tazId = username,
                idPassword = password,
                firstName = firstName,
                surname = surname
            )

            when (subscriptionInfo?.status) {
                SubscriptionStatus.tazIdNotValid -> {
                    // should not happen
                    status = LoginViewModelState.CREDENTIALS_MISSING_FAILED
                }
                SubscriptionStatus.alreadyLinked -> {
                    statusBeforeEmailAlreadyLinked = previousState
                    status = LoginViewModelState.EMAIL_ALREADY_LINKED
                }
                SubscriptionStatus.invalidMail -> {
                    status = invalidMailState
                }
                SubscriptionStatus.elapsed -> {
                    authHelper.email.set(username)
                    subscriptionInfo.token?.let { authHelper.token.set(it) }
                    status = LoginViewModelState.SUBSCRIPTION_ELAPSED
                }
                SubscriptionStatus.invalidConnection -> {
                    status = LoginViewModelState.SUBSCRIPTION_TAKEN
                }
                SubscriptionStatus.noPollEntry -> {
                    resetCredentialsPassword()
                    resetSubscriptionPassword()
                    status = LoginViewModelState.POLLING_FAILED
                }
                SubscriptionStatus.valid -> {
                    val token = requireNotNull(subscriptionInfo.token) {
                        "valid subscription needs a token"
                    }
                    authHelper.status.set(AuthStatus.valid)
                    authHelper.token.set(token)
                    authHelper.email.set(username)
                    status = LoginViewModelState.REGISTRATION_SUCCESSFUL
                    tracker.trackSubscriptionTrialConfirmedEvent()
                }
                SubscriptionStatus.waitForMail -> {
                    if (waitForMailSinceMs == 0L) {
                        waitForMailSinceMs = System.currentTimeMillis()
                    }
                    status = LoginViewModelState.REGISTRATION_EMAIL
                }
                SubscriptionStatus.waitForProc -> {
                    if (waitForMailSinceMs == 0L) {
                        waitForMailSinceMs = System.currentTimeMillis()
                    }
                    poll(previousState)
                }
                SubscriptionStatus.noFirstName, SubscriptionStatus.noSurname -> {
                    status = LoginViewModelState.NAME_MISSING
                }
                else -> {
                    status = previousState
                    SentryWrapper.captureMessage("trialSubscription returned ${subscriptionInfo?.status}")
                    toastHelper.showToast(R.string.toast_unknown_error)
                }
            }
        } catch (e: ConnectivityException) {
            status = previousState
            noInternet = true
        }
    }


    fun connect(): Job {
        val previousState = status
        status = LoginViewModelState.LOADING
        return launch {
            if (!createNewAccount) {
                val checkCredentials = checkCredentials()
                if (checkCredentials == false) {
                    status = LoginViewModelState.CREDENTIALS_MISSING_FAILED
                    return@launch
                } else if (checkCredentials == null) {
                    status = previousState
                    return@launch
                }
            }

            handleConnect(previousState)
        }
    }

    private suspend fun handleConnect(
        previousState: LoginViewModelState
    ) {
        try {
            val subscriptionInfo = apiService.subscriptionId2TazId(
                tazId = username!!,
                idPassword = password!!,
                subscriptionId = subscriptionId!!,
                subscriptionPassword = subscriptionPassword!!,
                firstName = firstName,
                surname = surName
            )

            when (subscriptionInfo?.status) {
                SubscriptionStatus.valid -> {
                    val token = requireNotNull(subscriptionInfo.token) {
                        "valid subscription needs a token"
                    }
                    authHelper.status.set(AuthStatus.valid)
                    authHelper.token.set(token)
                    authHelper.email.set(username ?: "")
                    status = LoginViewModelState.REGISTRATION_SUCCESSFUL
                }
                SubscriptionStatus.subscriptionIdNotValid -> {
                    status = LoginViewModelState.SUBSCRIPTION_MISSING_INVALID_ID
                }
                SubscriptionStatus.noFirstName,
                SubscriptionStatus.noSurname,
                SubscriptionStatus.nameTooLong,
                SubscriptionStatus.invalidMail -> {
                    resetCredentialsPassword()
                    status = LoginViewModelState.CREDENTIALS_MISSING_FAILED
                }
                SubscriptionStatus.waitForProc -> {
                    poll(previousState)
                }
                SubscriptionStatus.waitForMail -> {
                    status = LoginViewModelState.REGISTRATION_EMAIL
                }
                SubscriptionStatus.tazIdNotValid -> {
                    resetCredentialsPassword()
                    status = LoginViewModelState.CREDENTIALS_MISSING_FAILED
                }
                SubscriptionStatus.invalidConnection -> {
                    status = LoginViewModelState.SUBSCRIPTION_TAKEN
                }
                SubscriptionStatus.elapsed -> {
                    status = LoginViewModelState.SUBSCRIPTION_ELAPSED
                }
                SubscriptionStatus.noPollEntry -> {
                    resetCredentialsPassword()
                    resetSubscriptionPassword()
                    status = LoginViewModelState.POLLING_FAILED
                }
                SubscriptionStatus.alreadyLinked -> {
                    statusBeforeEmailAlreadyLinked = previousState
                    status =
                        if (validCredentials) {
                            LoginViewModelState.SUBSCRIPTION_ALREADY_LINKED
                        } else {
                            LoginViewModelState.EMAIL_ALREADY_LINKED
                        }
                }
                null -> {
                    status = previousState
                    noInternet = true
                }
                else -> {
                    // should not happen
                    SentryWrapper.captureMessage("connect returned ${subscriptionInfo.status}")
                    toastHelper.showSomethingWentWrongToast()
                    status = previousState
                }
            }
        } catch (e: ConnectivityException) {
            status = previousState
            noInternet = true
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun poll(
        previousState: LoginViewModelState,
        timeoutMillis: Long = 100
    ) {
        status = LoginViewModelState.LOADING

        launch {
            delay(timeoutMillis)
            handlePoll(previousState, timeoutMillis * 2)
        }
    }

    private suspend fun handlePoll(
        previousState: LoginViewModelState,
        timeoutMillis: Long
    ) {
        try {
            val subscriptionInfo = apiService.subscriptionPoll()
            log.debug("poll subscriptionPoll: $subscriptionInfo")

            when (subscriptionInfo?.status) {
                SubscriptionStatus.valid -> {
                    val token = requireNotNull(subscriptionInfo.token) {
                        "valid subscription needs a token"
                    }
                    authHelper.status.set(AuthStatus.valid)
                    authHelper.token.set(token)
                    authHelper.email.set(username ?: "")

                    tracker.trackSubscriptionTrialConfirmedEvent()
                    status = LoginViewModelState.REGISTRATION_SUCCESSFUL
                }
                SubscriptionStatus.elapsed -> {
                    status = LoginViewModelState.SUBSCRIPTION_ELAPSED
                }
                SubscriptionStatus.invalidConnection -> {
                    status = LoginViewModelState.SUBSCRIPTION_TAKEN
                }
                SubscriptionStatus.alreadyLinked -> {
                    statusBeforeEmailAlreadyLinked = previousState
                    status =
                        if (validCredentials) {
                            LoginViewModelState.SUBSCRIPTION_ALREADY_LINKED
                        } else {
                            LoginViewModelState.EMAIL_ALREADY_LINKED
                        }
                }
                SubscriptionStatus.waitForMail -> {
                    if (waitForMailSinceMs == 0L) {
                        waitForMailSinceMs = System.currentTimeMillis()
                    }
                    status = LoginViewModelState.REGISTRATION_EMAIL
                }
                null,
                SubscriptionStatus.waitForProc -> {
                    if (waitForMailSinceMs == 0L) {
                        waitForMailSinceMs = System.currentTimeMillis()
                    }
                    poll(previousState, timeoutMillis)
                }
                SubscriptionStatus.noPollEntry -> {
                    resetCredentialsPassword()
                    resetSubscriptionPassword()
                    status = LoginViewModelState.POLLING_FAILED
                }
                SubscriptionStatus.noSurname,
                SubscriptionStatus.noFirstName -> {
                    status = LoginViewModelState.NAME_MISSING
                }
                SubscriptionStatus.tooManyPollTries -> {
                    authHelper.isPolling.set(false)
                    SentryWrapper.captureMessage("ToManyPollTrys")
                }
                else -> {
                    // should not happen
                    SentryWrapper.captureMessage("connect returned ${subscriptionInfo.status}")
                    toastHelper.showSomethingWentWrongToast()
                }
            }
        } catch (e: ConnectivityException) {
            noInternet = true
            poll(previousState, timeoutMillis)
        }
    }

    fun requestPasswordReset(subscriptionId: Boolean = false) {
        if (status !in listOf(
                LoginViewModelState.PASSWORD_REQUEST,
                LoginViewModelState.PASSWORD_REQUEST_INVALID_MAIL,
                LoginViewModelState.PASSWORD_REQUEST_INVALID_ID,
                LoginViewModelState.PASSWORD_REQUEST_NO_MAIL,
                LoginViewModelState.PASSWORD_REQUEST_DONE
            )
        ) {
            statusBeforePasswordRequest = status
        }
        if (subscriptionId) {
            status = LoginViewModelState.PASSWORD_REQUEST_SUBSCRIPTION_ID
        } else {
            status = LoginViewModelState.PASSWORD_REQUEST
        }
    }

    fun requestSubscriptionPassword(subscriptionId: Int): Job {
        log.debug("forgotCredentialsPassword $subscriptionId")
        status = LoginViewModelState.LOADING
        return launch { handleSubscriptionPassword(subscriptionId) }
    }

    private suspend fun handleSubscriptionPassword(subscriptionId: Int) {
        try {
            val subscriptionResetInfo = apiService.requestSubscriptionPassword(subscriptionId)
            log.debug("handleSubscriptionPassword returned: subscriptionResetInfo: $subscriptionResetInfo")
            when (subscriptionResetInfo?.status) {
                SubscriptionResetStatus.ok ->
                    status = LoginViewModelState.PASSWORD_REQUEST_DONE
                SubscriptionResetStatus.invalidConnection -> {
                    status = LoginViewModelState.INITIAL
                    toastHelper.showToast(R.string.toast_login_with_email)
                }
                SubscriptionResetStatus.invalidSubscriptionId -> {
                    status = LoginViewModelState.PASSWORD_REQUEST_INVALID_ID
                }
                SubscriptionResetStatus.noMail -> {
                    status = LoginViewModelState.PASSWORD_REQUEST_NO_MAIL
                }
                SubscriptionResetStatus.UNKNOWN_RESPONSE,
                    // FIXME (johannes): show a generic message in case of unknown responses
                null -> {
                    status = LoginViewModelState.PASSWORD_REQUEST
                }
            }
        } catch (e: ConnectivityException) {
            status = LoginViewModelState.PASSWORD_REQUEST
            noInternet = true
        }
    }

    fun requestCredentialsPasswordReset(email: String): Job? {
        log.debug("forgotCredentialsPassword $email")
        return if (email.isEmpty()) {
            status = LoginViewModelState.PASSWORD_REQUEST
            null
        } else {
            status = LoginViewModelState.LOADING
            launch { handlePasswordReset(email) }
        }
    }

    private suspend fun handlePasswordReset(email: String) {
        try {
            when (apiService.requestCredentialsPasswordReset(email)) {
                PasswordResetInfo.ok -> {
                    status = LoginViewModelState.PASSWORD_REQUEST_DONE
                }
                PasswordResetInfo.invalidMail -> {
                    status = LoginViewModelState.PASSWORD_REQUEST_INVALID_MAIL
                }
                null,
                PasswordResetInfo.error,
                PasswordResetInfo.mailError -> {
                    toastHelper.showToast(R.string.something_went_wrong_try_later, long = true)
                    status = LoginViewModelState.PASSWORD_REQUEST
                }
            }
        } catch (e: ConnectivityException) {
            noInternet = true
            status = LoginViewModelState.PASSWORD_REQUEST
        }
    }

    fun backAfterEmailSent() {
        status = LoginViewModelState.LOADING
        val statusBefore = if (backToSettingsAfterEmailSent) {
            LoginViewModelState.DONE
        } else {
            statusBeforePasswordRequest ?: LoginViewModelState.INITIAL
        }
        status = statusBefore
        statusBeforePasswordRequest = null
    }

    private fun resetSubscriptionPassword() {
        subscriptionPassword = null
    }

    private fun resetCredentialsPassword() {
        password = null
    }

    private fun resetSubscriptionId() {
        subscriptionId = null
    }

    private suspend fun checkCredentials(): Boolean? {
        return try {
            val authTokenInfo = apiService.authenticate(username ?: "", password ?: "")
            authTokenInfo?.authInfo?.status != AuthStatus.notValid
        } catch (e: ConnectivityException) {
            status = LoginViewModelState.INITIAL
            noInternet = true
            null
        }
    }

    fun requestSubscription() = launch {
        val previousState = status
        status = LoginViewModelState.LOADING
        if (!createNewAccount) {
            val checkCredentials = checkCredentials()
            if (checkCredentials == false) {
                status = LoginViewModelState.SUBSCRIPTION_ACCOUNT_INVALID
                return@launch
            } else if (checkCredentials == null) {
                status = previousState
                return@launch
            }
        }

        if (createNewAccount || !validCredentials) {
            getTrialSubscriptionForNewCredentials(previousState)
        } else {
            getTrialSubscriptionForExistingCredentials(previousState)
        }
    }

    suspend fun isElapsed(): Boolean {
        return authHelper.isElapsed()
    }

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.IO
}

enum class LoginViewModelState {
    INITIAL,
    CREDENTIALS_INVALID,
    CREDENTIALS_MISSING_LOGIN,
    CREDENTIALS_MISSING_FAILED,
    CREDENTIALS_MISSING_REGISTER,
    EMAIL_ALREADY_LINKED,
    PASSWORD_MISSING,
    PASSWORD_REQUEST,
    PASSWORD_REQUEST_DONE,
    PASSWORD_REQUEST_SUBSCRIPTION_ID,
    PASSWORD_REQUEST_INVALID_MAIL,
    LOADING,
    LOGIN,
    PASSWORD_REQUEST_NO_MAIL,
    PASSWORD_REQUEST_INVALID_ID,
    POLLING_FAILED,
    REGISTRATION_EMAIL,
    REGISTRATION_SUCCESSFUL,
    SUBSCRIPTION_ACCOUNT,
    SUBSCRIPTION_ACCOUNT_INVALID,
    SUBSCRIPTION_NAME,
    SUBSCRIPTION_ALREADY_LINKED,
    SUBSCRIPTION_ELAPSED,
    SUBSCRIPTION_INVALID,
    SUBSCRIPTION_MISSING,
    SUBSCRIPTION_MISSING_INVALID_ID,
    SUBSCRIPTION_REQUEST,
    SUBSCRIPTION_REQUEST_INVALID_EMAIL,
    SUBSCRIPTION_TAKEN,
    USERNAME_MISSING,
    NAME_MISSING,
    SWITCH_PRINT_2_DIGI_REQUEST,
    EXTEND_PRINT_WITH_DIGI_REQUEST,
    DONE
}