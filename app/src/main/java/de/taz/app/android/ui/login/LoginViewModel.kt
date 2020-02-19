package de.taz.app.android.ui.login

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.PasswordResetInfo
import de.taz.app.android.api.models.SubscriptionStatus
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginViewModel(
    initialUsername: String? = null,
    initialPassword: String? = null,
    private val apiService: ApiService = ApiService.getInstance(),
    private val authHelper: AuthHelper = AuthHelper.getInstance()
) : ViewModel() {

    private val log by Log

    var username: String? = null
        private set
    var password: String? = null
        private set

    var subscriptionId: Int? = null
        private set
    var subscriptionPassword: String? = null
        private set

    private val status by lazy {
        MutableLiveData<LoginViewModelState>(LoginViewModelState.INITIAL)
    }

    fun observeStatus(lifecycleOwner: LifecycleOwner, observationCallback: (LoginViewModelState) -> Unit) {
        status.observe(lifecycleOwner, Observer(observationCallback))
    }

    private var statusBeforePasswordRequest: LoginViewModelState? = null

    private val noInternet by lazy {
        MutableLiveData<Boolean>(false)
    }

    fun observeNoInternet(lifecycleOwner: LifecycleOwner, observationCallback: (Boolean) -> Unit) {
        noInternet.observe(lifecycleOwner, Observer(observationCallback))
    }

    init {
        login(initialUsername, initialPassword)
    }

    fun backToMissingSubscription() {
        resetSubscriptionId()
        resetSubscriptionPassword()
        status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING)
    }

    fun login(initialUsername: String? = null, initialPassword: String? = null) {
        initialUsername?.toIntOrNull()?.let {
            subscriptionId = it
            subscriptionPassword = initialPassword

            subscriptionId?.let { subscriptionId ->
                subscriptionPassword?.let { subscriptionPassword ->
                    if (subscriptionPassword.isNotBlank()) {
                        status.postValue(LoginViewModelState.SUBSCRIPTION_CHECKING)

                        CoroutineScope(Dispatchers.IO).launch {

                            try {

                                val subscriptionAuthInfo = apiService.checkSubscriptionId(
                                    subscriptionId,
                                    subscriptionPassword
                                )

                                when (subscriptionAuthInfo?.status) {
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
                                    null -> noInternet.postValue(true)
                                }
                            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                                noInternet.postValue(true)
                            }
                        }
                    } else {
                        status.postValue(LoginViewModelState.PASSWORD_MISSING)
                    }
                }
            }
        } ?: run {
            initialUsername?.let { username = it }
            initialPassword?.let { password = it }

            username?.let { username ->
                password?.let { password ->

                    if (username.isNotBlank() && password.isNotBlank()) {

                        status.postValue(LoginViewModelState.CREDENTIALS_CHECKING)

                        CoroutineScope(Dispatchers.IO).launch {
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
                                    null -> noInternet.postValue(true)
                                }
                            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                                noInternet.postValue(true)
                            }
                        }
                    } else if (username.isBlank()) {
                        status.postValue(LoginViewModelState.USERNAME_MISSING)
                    } else {
                        status.postValue(LoginViewModelState.PASSWORD_MISSING)
                    }
                }
            }
        }
    }

    fun requestSubscription(username: String?) {
        if (!username.isNullOrEmpty() && username.toIntOrNull() == null) {
            this.username = username
            status.postValue(LoginViewModelState.SUBSCRIPTION_REQUEST)
        }
    }

    fun getTrialSubscriptionForExistingCredentials() {
        register(LoginViewModelState.CREDENTIALS_MISSING_INVALID_EMAIL)
    }

    fun getTrialSubscriptionForNewCredentials(username: String? = null, password: String? = null) {
        register(LoginViewModelState.SUBSCRIPTION_REQUEST_INVALID_EMAIL, username, password)
    }

    private fun register(
        invalidMailState: LoginViewModelState,
        username: String? = null,
        password: String? = null
    ) {

        username?.let { this.username = it }
        password?.let { this.password = it }

        this.username?.let { username1 ->
            this.password?.let { password1 ->
                status.postValue(LoginViewModelState.REGISTRATION_CHECKING)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val subscriptionInfo = apiService.trialSubscription(username1, password1)

                        when (subscriptionInfo?.status) {
                            SubscriptionStatus.subscriptionIdNotValid -> {
                                // should not happen
                                Sentry.capture("trialSubscription returned aboIdNotValid")
                                status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING)
                            }
                            SubscriptionStatus.tazIdNotValid -> {
                                // should not happen - TODO currently if user has not clicked mail link
                                Sentry.capture("trialSubscription returned tazIdNotValid")
                                status.postValue(LoginViewModelState.CREDENTIALS_MISSING)
                            }
                            SubscriptionStatus.alreadyLinked -> {
                                // TODO check if can login then auto login? Or show screen?
                                login(username1, password1)
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
                                // TODO?
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
                                noInternet.postValue(true)
                            }
                        }
                    } catch (e: ApiService.ApiServiceException.NoInternetException) {
                        noInternet.postValue(true)
                    }
                }
            }
        }
    }

    fun connect(
        username: String? = null,
        password: String? = null,
        subscriptionId: Int? = null,
        subscriptionPassword: String? = null
    ) {
        username?.let { this.username = it }
        password?.let { this.password = it }
        subscriptionId?.let { this.subscriptionId = it }
        subscriptionPassword?.let { this.subscriptionPassword = it }

        CoroutineScope(Dispatchers.IO).launch {
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
                    SubscriptionStatus.noPollEntry,
                    SubscriptionStatus.alreadyLinked -> {
                        // should not happen
                        Sentry.capture("subscriptionId2TazId returned ${subscriptionInfo.status}")
                        // TODO what to do?
                    }
                    null -> {
                        noInternet.postValue(true)
                    }

                }
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                noInternet.postValue(true)
            }
        }
    }


    private fun poll(timeoutMillis: Long = 100) {
        status.postValue(LoginViewModelState.REGISTRATION_CHECKING)

        CoroutineScope(Dispatchers.IO).launch {
            delay(timeoutMillis)

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
                        // TODO check if can login then auto login? Or show screen?
                        login(username, password)
                    }
                    SubscriptionStatus.waitForMail -> {
                        status.postValue(LoginViewModelState.REGISTRATION_EMAIL)
                    }
                    SubscriptionStatus.waitForProc -> {
                        poll(timeoutMillis * 2)
                    }
                    SubscriptionStatus.noPollEntry -> {
                        // TODO?
                    }
                }
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                noInternet.postValue(true)
            }
        }
    }

    fun requestPasswordReset() {
        statusBeforePasswordRequest = status.value
        status.postValue(LoginViewModelState.PASSWORD_REQUEST)
    }


    fun requestSubscriptionPassword(subscriptionId: Int) {
        log.debug("forgotCredentialsPassword $subscriptionId")
        status.postValue(LoginViewModelState.PASSWORD_REQUEST_ONGOING)

        /* TODO ONCE IMPLEMENTED ON SERVER
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (apiService.requestSubscriptionPassword(subscriptionId)) {
                    PasswordResetInfo.ok ->
                        status.postValue(LoginViewModelState.PASSWORD_REQUEST_DONE)
                    PasswordResetInfo.error,
                    PasswordResetInfo.invalidMail,
                    PasswordResetInfo.mailError -> {
                        ToastHelper.getInstance()
                            .makeToast(R.string.something_went_wrong_try_later)
                        status.postValue(LoginViewModelState.PASSWORD_REQUEST)
                    }
                }
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                noInternet.postValue(true)
            }
        }
         */

        // TODO remove and use above
        status.postValue(LoginViewModelState.PASSWORD_REQUEST_DONE)
    }

    fun requestCredentialsPasswordReset(email: String) {
        log.debug("forgotCredentialsPassword $email")
        if (email.isEmpty()) {
            status.postValue(LoginViewModelState.PASSWORD_REQUEST)
        } else {
            status.postValue(LoginViewModelState.PASSWORD_REQUEST_ONGOING)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    when (apiService.requestCredentialsPasswordReset(email)) {
                        PasswordResetInfo.ok -> {
                            status.postValue(LoginViewModelState.PASSWORD_REQUEST_DONE)
                        }
                        PasswordResetInfo.error,
                        PasswordResetInfo.invalidMail,
                        PasswordResetInfo.mailError -> {
                            ToastHelper.getInstance()
                                .makeToast(R.string.something_went_wrong_try_later)
                            status.postValue(LoginViewModelState.PASSWORD_REQUEST)
                        }
                    }
                } catch (e: ApiService.ApiServiceException.NoInternetException) {
                    noInternet.postValue(true)
                }
            }
        }
    }

    fun backAfterEmailSent() {
        status.postValue(statusBeforePasswordRequest)
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

    private fun saveToken(token: String) {
        authHelper.tokenLiveData.postValue(token)
        authHelper.authStatusLiveData.postValue(AuthStatus.valid)
        authHelper.emailLiveData.postValue(username)
    }

}

enum class LoginViewModelState {
    INITIAL,
    CREDENTIALS_CHECKING,
    CREDENTIALS_INVALID,
    CREDENTIALS_MISSING,
    CREDENTIALS_MISSING_INVALID_EMAIL,
    SUBSCRIPTION_CHECKING,
    SUBSCRIPTION_ELAPSED,
    SUBSCRIPTION_INVALID,
    SUBSCRIPTION_MISSING,
    SUBSCRIPTION_MISSING_INVALID_ID,
    SUBSCRIPTION_REQUEST,
    SUBSCRIPTION_REQUEST_INVALID_EMAIL,
    SUBSCRIPTION_TAKEN,
    PASSWORD_MISSING,
    PASSWORD_REQUEST,
    PASSWORD_REQUEST_DONE,
    PASSWORD_REQUEST_ONGOING,
    REGISTRATION_CHECKING,
    REGISTRATION_EMAIL,
    REGISTRATION_SUCCESSFUL,
    USERNAME_MISSING,
    DONE
}