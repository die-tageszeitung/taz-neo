package de.taz.app.android.ui.login

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
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
    application: Application,
    initialUsername: String? = null,
    initialPassword: String? = null,
    register: Boolean = false,
    private val apiService: ApiService = ApiService.getInstance(application),
    private val authHelper: AuthHelper = AuthHelper.getInstance(application),
    private val toastHelper: ToastHelper = ToastHelper.getInstance(application)
) : AndroidViewModel(application) {

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
        if (!register && !initialUsername.isNullOrBlank() && !initialPassword.isNullOrBlank()) {
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
        val initialSubscriptionId = initialUsername?.toIntOrNull()
        return if (initialSubscriptionId != null) {
            subscriptionId = initialSubscriptionId
            subscriptionPassword = initialPassword

            status.postValue(LoginViewModelState.LOADING)
            if (!initialPassword.isNullOrBlank()) {
                ioScope.launch {
                    handleSubscriptionIdLogin(initialSubscriptionId, initialPassword)
                }
            } else {
                status.postValue(LoginViewModelState.PASSWORD_MISSING)
                null
            }

        } else {
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
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING_REGISTER)
                }
                AuthStatus.elapsed ->
                    status.postValue(LoginViewModelState.SUBSCRIPTION_ELAPSED)
                AuthStatus.notValidMail,
                AuthStatus.notValid -> {
                    resetSubscriptionPassword()
                    status.postValue(LoginViewModelState.SUBSCRIPTION_INVALID)
                }
                AuthStatus.valid ->
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING_REGISTER)
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
                    status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING)
                }
                AuthStatus.notValidMail -> {
                    // this should never happen
                    toastHelper.showSomethingWentWrongToast()
                    status.postValue(LoginViewModelState.INITIAL)
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

    fun getTrialSubscriptionForExistingCredentials(
        firstName: String? = null,
        surname: String? = null
    ) {
        register(
            LoginViewModelState.CREDENTIALS_MISSING_REGISTER_FAILED,
            firstName = firstName,
            surname = surname
        )
    }

    fun getTrialSubscriptionForNewCredentials(
        username: String? = null,
        password: String? = null,
        firstName: String? = null,
        surname: String? = null
    ) {
        register(
            LoginViewModelState.SUBSCRIPTION_REQUEST_INVALID_EMAIL,
            username,
            password,
            firstName,
            surname
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun register(
        invalidMailState: LoginViewModelState,
        username: String? = null,
        password: String? = null,
        firstName: String? = null,
        surname: String? = null
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
                    firstName,
                    surname,
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
        previousState: LoginViewModelState?
    ) {
        try {
            val subscriptionInfo = apiService.trialSubscription(
                tazId = username,
                idPassword = password,
                firstName = firstName,
                surname = surname
            )

            when (subscriptionInfo?.status) {
                SubscriptionStatus.subscriptionIdNotValid -> {
                    // should not happen
                    status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING)
                }
                SubscriptionStatus.tazIdNotValid -> {
                    // should not happen
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING_REGISTER_FAILED)
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
                SubscriptionStatus.noFirstName, SubscriptionStatus.noSurname -> {
                    status.postValue(LoginViewModelState.NAME_MISSING)
                }
                else -> {
                    status.postValue(previousState)
                    toastHelper.showToast(R.string.toast_unknown_error)
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
        subscriptionPassword: String? = null,
        firstName: String? = null,
        surname: String? = null
    ): Job {
        val previousState = status.value
        status.postValue(LoginViewModelState.LOADING)

        username?.let { this.username = it }
        password?.let { this.password = it }
        subscriptionId?.let { this.subscriptionId = it }
        subscriptionPassword?.let { this.subscriptionPassword = it }

        return ioScope.launch { handleConnect(previousState, firstName, surname) }
    }

    private suspend fun handleConnect(
        previousState: LoginViewModelState?,
        firstName: String?,
        surname: String?
    ) {
        try {
            val subscriptionInfo = apiService.subscriptionId2TazId(
                tazId = this@LoginViewModel.username!!,
                idPassword = this@LoginViewModel.password!!,
                subscriptionId = this@LoginViewModel.subscriptionId!!,
                subscriptionPassword = this@LoginViewModel.subscriptionPassword!!,
                firstName = firstName,
                surname = surname
            )

            when (subscriptionInfo?.status) {
                SubscriptionStatus.valid -> {
                    saveToken(subscriptionInfo.token!!)
                    status.postValue(LoginViewModelState.REGISTRATION_SUCCESSFUL)
                }
                SubscriptionStatus.subscriptionIdNotValid -> {
                    status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING_INVALID_ID)
                }
                SubscriptionStatus.noFirstName,
                SubscriptionStatus.noSurname,
                SubscriptionStatus.invalidMail -> {
                    resetCredentialsPassword()
                    status.postValue(
                        if (previousState == LoginViewModelState.CREDENTIALS_MISSING_LOGIN) {
                            LoginViewModelState.CREDENTIALS_MISSING_LOGIN_FAILED
                        } else {
                            LoginViewModelState.CREDENTIALS_MISSING_REGISTER_FAILED
                        }
                    )
                }
                SubscriptionStatus.waitForProc -> {
                    poll()
                }
                SubscriptionStatus.waitForMail -> {
                    status.postValue(LoginViewModelState.REGISTRATION_EMAIL)
                }
                SubscriptionStatus.tazIdNotValid -> {
                    resetCredentialsPassword()
                    status.postValue(
                        if (previousState == LoginViewModelState.CREDENTIALS_MISSING_LOGIN) {
                            LoginViewModelState.CREDENTIALS_MISSING_LOGIN_FAILED
                        } else {
                            LoginViewModelState.CREDENTIALS_MISSING_REGISTER_FAILED
                        }
                    )
                }
                SubscriptionStatus.invalidConnection -> {
                    status.postValue(LoginViewModelState.SUBSCRIPTION_TAKEN)
                }
                SubscriptionStatus.elapsed -> {
                    status.postValue(LoginViewModelState.SUBSCRIPTION_ELAPSED)
                }
                SubscriptionStatus.noPollEntry -> {
                    resetCredentialsPassword()
                    resetSubscriptionPassword()
                    status.postValue(LoginViewModelState.POLLING_FAILED)
                }
                SubscriptionStatus.alreadyLinked -> {
                    // should not happen
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
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING_REGISTER)
                }
                SubscriptionStatus.subscriptionIdNotValid -> {
                    // should not happen
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
                SubscriptionStatus.noSurname,
                SubscriptionStatus.noFirstName -> {
                    status.postValue(LoginViewModelState.NAME_MISSING)
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
    CREDENTIALS_MISSING_LOGIN,
    CREDENTIALS_MISSING_LOGIN_FAILED,
    CREDENTIALS_MISSING_REGISTER,
    CREDENTIALS_MISSING_REGISTER_FAILED,
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
    NAME_MISSING,
    DONE
}