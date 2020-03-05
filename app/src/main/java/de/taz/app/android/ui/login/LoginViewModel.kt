package de.taz.app.android.ui.login

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.PasswordResetInfo
import de.taz.app.android.api.models.SubscriptionResetStatus
import de.taz.app.android.api.models.SubscriptionStatus
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import io.sentry.Sentry
import kotlinx.coroutines.*

class LoginViewModel(
    initialUsername: String? = null,
    initialPassword: String? = null,
    register: Boolean = false,
    private val apiService: ApiService = ApiService.getInstance(),
    private val authHelper: AuthHelper = AuthHelper.getInstance(),
    private val toastHelper: ToastHelper = ToastHelper.getInstance()
) : ViewModel() {

    private val log by Log
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var statusBeforePasswordRequest: LoginViewModelState? = null

    val status by lazy { MutableLiveData(LoginViewModelState.INITIAL) }
    val noInternet by lazy { MutableLiveData(false) }

    var username: String? = null
        private set
    var password: String? = null
        private set
    var subscriptionId: Int? = null
        private set
    var subscriptionPassword: String? = null
        private set
    var backToArticle: Boolean = true

    init {
        initialUsername?.let { username = it }
        initialPassword?.let { password = it }
        if (!register && !username.isNullOrBlank() && !password.isNullOrBlank()) {
            login(initialUsername, initialPassword)
        }
    }

    fun startPolling() {
        authHelper.email = username
        authHelper.isPolling = true
        status.postValue(LoginViewModelState.DONE)
    }

    fun backToMissingSubscription() {
        resetSubscriptionId()
        resetSubscriptionPassword()
        status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING)
    }

    fun login(initialUsername: String? = null, initialPassword: String? = null): Job? {
        return initialUsername?.toIntOrNull()?.let {
            subscriptionId = it
            subscriptionPassword = initialPassword

            runIfNotNull(
                subscriptionId,
                subscriptionPassword
            ) { subscriptionId, subscriptionPassword ->
                status.postValue(LoginViewModelState.LOADING)
                if (subscriptionPassword.isNotBlank()) {
                    ioScope.launch {
                        handleSubscriptionIdLogin(subscriptionId, subscriptionPassword)
                    }
                } else {
                    status.postValue(LoginViewModelState.PASSWORD_MISSING)
                    null
                }
            }
        } ?: run {
            initialUsername?.let { username = it }
            initialPassword?.let { password = it }

            status.postValue(LoginViewModelState.LOADING)

            val tmpUsername = username
            val tmpPassword = password

            if (tmpUsername.isNullOrBlank()) {
                status.postValue(LoginViewModelState.USERNAME_MISSING)
                null
            } else if (tmpPassword.isNullOrBlank()) {
                status.postValue(LoginViewModelState.PASSWORD_MISSING)
                null
            } else {
                ioScope.launch { handleCredentialsLogin(tmpUsername, tmpPassword) }
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
                    status.postValue(LoginViewModelState.INITIAL)
                    toastHelper.showToast(R.string.toast_login_with_email)
                }
                AuthStatus.tazIdNotLinked -> {
                    // this should never happen
                    Sentry.capture("checkSubscriptionId returned tazIdNotLinked")
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING)
                }
                AuthStatus.elapsed ->
                    status.postValue(LoginViewModelState.SUBSCRIPTION_ELAPSED)

                AuthStatus.notValid -> {
                    resetSubscriptionPassword()
                    status.postValue(LoginViewModelState.SUBSCRIPTION_INVALID)
                }
                AuthStatus.valid ->
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING)
                null -> {
                    status.postValue(LoginViewModelState.INITIAL)
                    noInternet.postValue(true)
                }
            }
        } catch (e: ApiService.ApiServiceException.NoInternetException) {
            status.postValue(LoginViewModelState.INITIAL)
            noInternet.postValue(true)
        }
    }

    private suspend fun handleCredentialsLogin(username: String, password: String) {
        try {
            val authTokenInfo = apiService.authenticate(username, password)

            when (authTokenInfo?.authInfo?.status) {
                AuthStatus.valid -> {
                    saveToken(authTokenInfo.token!!)
                    status.postValue(LoginViewModelState.DONE)
                }
                AuthStatus.notValid -> {
                    resetCredentialsPassword()
                    status.postValue(LoginViewModelState.CREDENTIALS_INVALID)
                }
                AuthStatus.tazIdNotLinked ->
                    status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING)
                AuthStatus.elapsed ->
                    status.postValue(LoginViewModelState.SUBSCRIPTION_ELAPSED)
                AuthStatus.alreadyLinked -> {
                    // this should never happen
                    Sentry.capture("authenticate returned alreadyLinked")
                    status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING)
                }
                null -> {
                    status.postValue(LoginViewModelState.INITIAL)
                    noInternet.postValue(true)
                }
            }
        } catch (e: ApiService.ApiServiceException.NoInternetException) {
            status.postValue(LoginViewModelState.INITIAL)
            noInternet.postValue(true)
        }
    }

    fun requestSubscription(username: String? = null) {
        if (!username.isNullOrEmpty() && username.toIntOrNull() == null) {
            this.username = username
        }
        status.postValue(LoginViewModelState.SUBSCRIPTION_REQUEST)
    }

    fun getTrialSubscriptionForExistingCredentials() {
        register(LoginViewModelState.CREDENTIALS_MISSING_INVALID_EMAIL)
    }

    fun getTrialSubscriptionForNewCredentials(username: String? = null, password: String? = null) {
        register(LoginViewModelState.SUBSCRIPTION_REQUEST_INVALID_EMAIL, username, password)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun register(
        invalidMailState: LoginViewModelState,
        username: String? = null,
        password: String? = null
    ): Job? {
        username?.let { this.username = it }
        password?.let { this.password = it }

        return runIfNotNull(this.username, this.password) { username1, password1 ->
            val previousState = status.value
            status.postValue(LoginViewModelState.LOADING)
            ioScope.launch {
                handleRegistration(
                    username1,
                    password1,
                    invalidMailState,
                    previousState
                )
            }
        }
    }

    private suspend fun handleRegistration(
        username: String,
        password: String,
        invalidMailState: LoginViewModelState,
        previousState: LoginViewModelState?
    ) {
        try {
            val subscriptionInfo = apiService.trialSubscription(username, password)

            when (subscriptionInfo?.status) {
                SubscriptionStatus.subscriptionIdNotValid -> {
                    // should not happen
                    Sentry.capture("trialSubscription returned subscriptionIdNotValid")
                    status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING)
                }
                SubscriptionStatus.tazIdNotValid -> {
                    // should not happen
                    Sentry.capture("trialSubscription returned tazIdNotValid")
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING_INVALID_EMAIL)
                }
                SubscriptionStatus.alreadyLinked -> {
                    status.postValue(LoginViewModelState.EMAIL_ALREADY_LINKED)
                }
                SubscriptionStatus.invalidMail -> {
                    status.postValue(invalidMailState)
                }
                SubscriptionStatus.elapsed -> {
                    status.postValue(LoginViewModelState.SUBSCRIPTION_ELAPSED)
                }
                SubscriptionStatus.invalidConnection -> {
                    status.postValue(LoginViewModelState.SUBSCRIPTION_TAKEN)
                }
                SubscriptionStatus.noPollEntry -> {
                    Sentry.capture("trialSubscription returned noPollEntry")
                    resetCredentialsPassword()
                    resetSubscriptionPassword()
                    status.postValue(LoginViewModelState.POLLING_FAILED)
                }
                SubscriptionStatus.valid -> {
                    saveToken(subscriptionInfo.token!!)
                    status.postValue(LoginViewModelState.REGISTRATION_SUCCESSFUL)
                }
                SubscriptionStatus.waitForMail -> {
                    status.postValue(LoginViewModelState.REGISTRATION_EMAIL)
                }
                SubscriptionStatus.waitForProc -> {
                    poll()
                }
                null -> {
                    status.postValue(previousState)
                    noInternet.postValue(true)
                }
            }
        } catch (e: ApiService.ApiServiceException.NoInternetException) {
            status.postValue(previousState)
            noInternet.postValue(true)
        }
    }


    fun connect(
        username: String? = null,
        password: String? = null,
        subscriptionId: Int? = null,
        subscriptionPassword: String? = null
    ): Job {
        val previousState = status.value
        status.postValue(LoginViewModelState.LOADING)

        username?.let { this.username = it }
        password?.let { this.password = it }
        subscriptionId?.let { this.subscriptionId = it }
        subscriptionPassword?.let { this.subscriptionPassword = it }

        return ioScope.launch { handleConnect(previousState) }
    }

    private suspend fun handleConnect(
        previousState: LoginViewModelState?
    ) {
        try {
            val subscriptionInfo = apiService.subscriptionId2TazId(
                this@LoginViewModel.username!!,
                this@LoginViewModel.password!!,
                this@LoginViewModel.subscriptionId!!,
                this@LoginViewModel.subscriptionPassword!!
            )

            when (subscriptionInfo?.status) {
                SubscriptionStatus.valid -> {
                    saveToken(subscriptionInfo.token!!)
                    status.postValue(LoginViewModelState.REGISTRATION_SUCCESSFUL)
                }
                SubscriptionStatus.subscriptionIdNotValid -> {
                    status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING_INVALID_ID)
                }
                SubscriptionStatus.invalidMail -> {
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING_INVALID_EMAIL)
                }
                SubscriptionStatus.waitForProc -> {
                    poll()
                }
                SubscriptionStatus.waitForMail -> {
                    status.postValue(LoginViewModelState.REGISTRATION_EMAIL)
                }
                SubscriptionStatus.tazIdNotValid -> {
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING)
                }
                SubscriptionStatus.invalidConnection -> {
                    status.postValue(LoginViewModelState.SUBSCRIPTION_TAKEN)
                }
                SubscriptionStatus.elapsed -> {
                    status.postValue(LoginViewModelState.SUBSCRIPTION_ELAPSED)
                }
                SubscriptionStatus.noPollEntry -> {
                    Sentry.capture("subscriptionId2TazId returned noPollEntry")
                    resetCredentialsPassword()
                    resetSubscriptionPassword()
                    status.postValue(LoginViewModelState.POLLING_FAILED)
                }
                SubscriptionStatus.alreadyLinked -> {
                    // should not happen
                    Sentry.capture("subscriptionId2TazId returned alreadyLinked")
                    status.postValue(LoginViewModelState.EMAIL_ALREADY_LINKED)
                }
                null -> {
                    status.postValue(previousState)
                    noInternet.postValue(true)
                }

            }
        } catch (e: ApiService.ApiServiceException.NoInternetException) {
            status.postValue(previousState)
            noInternet.postValue(true)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun poll(
        timeoutMillis: Long = 100,
        @VisibleForTesting(otherwise = VisibleForTesting.NONE) runBlocking: Boolean = false
    ): Job {
        status.postValue(LoginViewModelState.LOADING)

        return ioScope.launch {
            delay(timeoutMillis)
            handlePoll(timeoutMillis * 2, runBlocking)
        }
    }

    private suspend fun handlePoll(
        timeoutMillis: Long,
        @VisibleForTesting(otherwise = VisibleForTesting.NONE) runBlocking: Boolean = false
    ) {
        try {
            val subscriptionInfo = apiService.subscriptionPoll()
            log.debug("poll subscriptionPoll: $subscriptionInfo")

            when (subscriptionInfo?.status) {
                SubscriptionStatus.valid -> {
                    saveToken(subscriptionInfo.token!!)
                    status.postValue(LoginViewModelState.REGISTRATION_SUCCESSFUL)
                }
                SubscriptionStatus.tazIdNotValid -> {
                    // should not happen
                    Sentry.capture("trialSubscription returned tazIdNotValid")
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING)
                }
                SubscriptionStatus.subscriptionIdNotValid -> {
                    // should not happen
                    Sentry.capture("trialSubscription returned aboIdNotValid")
                    status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING)
                }
                SubscriptionStatus.elapsed -> {
                    status.postValue(LoginViewModelState.SUBSCRIPTION_ELAPSED)
                }
                SubscriptionStatus.invalidConnection -> {
                    status.postValue(LoginViewModelState.SUBSCRIPTION_TAKEN)
                }
                SubscriptionStatus.invalidMail -> {
                    // should never happen
                    Sentry.capture("subscriptionPoll returned invalidMail")
                    resetCredentialsPassword()
                    status.postValue(LoginViewModelState.CREDENTIALS_INVALID)
                }
                SubscriptionStatus.alreadyLinked -> {
                    status.postValue(LoginViewModelState.EMAIL_ALREADY_LINKED)
                }
                SubscriptionStatus.waitForMail -> {
                    status.postValue(LoginViewModelState.REGISTRATION_EMAIL)
                }
                null,
                SubscriptionStatus.waitForProc -> {
                    if (runBlocking) {
                        poll(timeoutMillis, runBlocking).join()
                    } else {
                        poll(timeoutMillis)
                    }
                }
                SubscriptionStatus.noPollEntry -> {
                    resetCredentialsPassword()
                    resetSubscriptionPassword()
                    status.postValue(LoginViewModelState.POLLING_FAILED)
                }
            }
        } catch (e: ApiService.ApiServiceException.NoInternetException) {
            noInternet.postValue(true)
            if (runBlocking) {
                poll(timeoutMillis, runBlocking).join()
            } else {
                poll(timeoutMillis)
            }

        }
    }

    fun requestPasswordReset() {
        statusBeforePasswordRequest = status.value
        status.postValue(LoginViewModelState.PASSWORD_REQUEST)
    }

    fun requestSubscriptionPassword(subscriptionId: Int): Job {
        log.debug("forgotCredentialsPassword $subscriptionId")
        status.postValue(LoginViewModelState.LOADING)
        return ioScope.launch { handleSubscriptionPassword(subscriptionId) }
    }

    private suspend fun handleSubscriptionPassword(subscriptionId: Int) {
        try {
            val subscriptionResetInfo = apiService.requestSubscriptionPassword(subscriptionId)
            when (subscriptionResetInfo?.status) {
                SubscriptionResetStatus.ok ->
                    status.postValue(LoginViewModelState.PASSWORD_REQUEST_DONE)
                SubscriptionResetStatus.invalidConnection -> {
                    username = subscriptionResetInfo.mail
                    status.postValue(LoginViewModelState.INITIAL)
                    toastHelper.showToast(R.string.toast_login_with_email)
                }
                SubscriptionResetStatus.invalidSubscriptionId -> {
                    status.postValue(LoginViewModelState.PASSWORD_REQUEST_INVALID_ID)
                }
                SubscriptionResetStatus.noMail -> {
                    status.postValue(LoginViewModelState.PASSWORD_REQUEST_NO_MAIL)
                }
                null -> {
                    status.postValue(LoginViewModelState.PASSWORD_REQUEST)
                }
            }
        } catch (e: ApiService.ApiServiceException.NoInternetException) {
            status.postValue(LoginViewModelState.PASSWORD_REQUEST)
            noInternet.postValue(true)
        }
    }

    fun requestCredentialsPasswordReset(email: String): Job? {
        log.debug("forgotCredentialsPassword $email")
        return if (email.isEmpty()) {
            status.postValue(LoginViewModelState.PASSWORD_REQUEST)
            null
        } else {
            status.postValue(LoginViewModelState.LOADING)
            ioScope.launch { handlePasswordReset(email) }
        }
    }

    private suspend fun handlePasswordReset(email: String) {
        try {
            when (apiService.requestCredentialsPasswordReset(email)) {
                PasswordResetInfo.ok -> {
                    status.postValue(LoginViewModelState.PASSWORD_REQUEST_DONE)
                }
                null,
                PasswordResetInfo.error,
                PasswordResetInfo.invalidMail,
                PasswordResetInfo.mailError -> {
                    toastHelper.showToast(R.string.something_went_wrong_try_later)
                    status.postValue(LoginViewModelState.PASSWORD_REQUEST)
                }
            }
        } catch (e: ApiService.ApiServiceException.NoInternetException) {
            noInternet.postValue(true)
            status.postValue(LoginViewModelState.PASSWORD_REQUEST)
        }
    }

    fun backAfterEmailSent() {
        status.postValue(LoginViewModelState.LOADING)
        status.postValue(statusBeforePasswordRequest)
        statusBeforePasswordRequest = null
    }

    fun backToLogin() {
        status.postValue(LoginViewModelState.LOADING)
        resetSubscriptionPassword()
        resetCredentialsPassword()
        resetSubscriptionId()
        status.postValue(LoginViewModelState.INITIAL)
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

    private fun saveToken(token: String) {
        authHelper.token = token
        authHelper.authStatus = AuthStatus.valid
        authHelper.email = username
    }

}

enum class LoginViewModelState {
    INITIAL,
    CREDENTIALS_INVALID,
    CREDENTIALS_MISSING,
    CREDENTIALS_MISSING_INVALID_EMAIL,
    EMAIL_ALREADY_LINKED,
    PASSWORD_MISSING,
    PASSWORD_REQUEST,
    PASSWORD_REQUEST_DONE,
    LOADING,
    PASSWORD_REQUEST_NO_MAIL,
    PASSWORD_REQUEST_INVALID_ID,
    POLLING_FAILED,
    REGISTRATION_EMAIL,
    REGISTRATION_SUCCESSFUL,
    SUBSCRIPTION_ELAPSED,
    SUBSCRIPTION_INVALID,
    SUBSCRIPTION_MISSING,
    SUBSCRIPTION_MISSING_INVALID_ID,
    SUBSCRIPTION_REQUEST,
    SUBSCRIPTION_REQUEST_INVALID_EMAIL,
    SUBSCRIPTION_TAKEN,
    USERNAME_MISSING,
    DONE
}