package de.taz.app.android.ui.login

import androidx.lifecycle.MutableLiveData
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

    private var username: String? = null
    private var password: String? = null

    private var subscriptionId: Int? = null
    private var subscriptionPassword: String? = null

    val status by lazy {
        MutableLiveData<LoginViewModelState>(LoginViewModelState.INITIAL)
    }

    val noInternet by lazy {
        MutableLiveData<Boolean>(false)
    }

    init {
        login(initialUsername, initialPassword)
    }

    fun login(initialUsername: String?, initialPassword: String?) {
        initialUsername?.toIntOrNull()?.let {
            subscriptionId = it
            subscriptionPassword = initialPassword

            subscriptionId?.let { subscriptionId ->
                subscriptionPassword?.let { subscriptionPassword ->
                    if (subscriptionPassword.isNotBlank()) {
                        status.postValue(LoginViewModelState.SUBSCRIPTION_CHECKING)

                        CoroutineScope(Dispatchers.IO).launch {
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
                                    password = null
                                    status.postValue(LoginViewModelState.SUBSCRIPTION_INVALID)
                                }
                                AuthStatus.valid ->
                                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING)
                                null -> noInternet.postValue(true)
                            }
                        }
                    } else {
                        status.postValue(LoginViewModelState.PASSWORD_MISSING)
                    }
                }
            }
        } ?: run {
            username = initialUsername
            password = initialPassword

            username?.let { username ->
                password?.let { password ->

                    if (username.isNotBlank() && password.isNotBlank()) {

                        status.postValue(LoginViewModelState.CREDENTIALS_CHECKING)

                        CoroutineScope(Dispatchers.IO).launch {
                            val authTokenInfo = apiService.authenticate(username, password)

                            when (authTokenInfo?.authInfo?.status) {
                                AuthStatus.valid -> {
                                    saveToken(authTokenInfo.token!!)
                                    status.postValue(LoginViewModelState.DONE)
                                }
                                AuthStatus.notValid ->
                                    status.postValue(LoginViewModelState.CREDENTIALS_INVALID)
                                AuthStatus.tazIdNotLinked ->
                                    status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING)
                                AuthStatus.elapsed ->
                                    status.postValue(LoginViewModelState.SUBSCRIPTION_ELAPSED)
                                null -> noInternet.postValue(true)
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

    fun register() {
        username?.let { username ->
            password?.let { password ->
                status.postValue(LoginViewModelState.REGISTRATION_CHECKING)

                CoroutineScope(Dispatchers.IO).launch {
                    val subscriptionInfo = apiService.trialSubscription(username, password)

                    when (subscriptionInfo?.status) {
                        SubscriptionStatus.aboIdNotValid -> {
                            // should not happen
                            Sentry.capture("trialSubscription returned aboIdNotValid")
                            status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING)
                        }
                        SubscriptionStatus.tazIdNotValid -> {
                            // should not happen
                            Sentry.capture("trialSubscription returned tazIdNotValid")
                            status.postValue(LoginViewModelState.CREDENTIALS_MISSING)
                        }
                        SubscriptionStatus.alreadyLinked -> {
                            // TODO check if can login then auto login? Or show screen?
                            login(username, password)
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
                }

            }
        }
    }

    private fun poll(timeoutMillis: Long = 100) {
        status.postValue(LoginViewModelState.REGISTRATION_CHECKING)

        CoroutineScope(Dispatchers.IO).launch {
            delay(timeoutMillis)

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
                SubscriptionStatus.aboIdNotValid -> {
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
                SubscriptionStatus.alreadyLinked -> {
                    // TODO check if can login then auto login? Or show screen?
                    login(username, password)
                }
                SubscriptionStatus.waitForMail -> {
                    status.postValue(LoginViewModelState.REGISTRATION_EMAIL)
                }
                SubscriptionStatus.waitForProc -> {
                    poll(timeoutMillis*2)
                }
                SubscriptionStatus.noPollEntry -> {
                    // TODO?
                }
            }
        }
    }

    fun resetCredentialsPassword(email: String) {
        log.debug("forgotCredentialsPassword $email")

        CoroutineScope(Dispatchers.IO).launch {
            status.postValue(LoginViewModelState.PASSWORD_REQUESTING)
            when (apiService.resetPassword(email)) {
                PasswordResetInfo.ok ->
                    status.postValue(LoginViewModelState.PASSWORD_REQUESTED)
                PasswordResetInfo.error,
                PasswordResetInfo.invalidMail, // should not happen?
                PasswordResetInfo.mailError -> {
                    ToastHelper.getInstance().makeToast(R.string.something_went_wrong_try_later)
                    // TODO hide loadingscreen?
                }
            }
        }
    }

    fun getUsername(): String? {
        return username ?: subscriptionId?.toString()
    }

    fun resetPassword() {
        password = null
        subscriptionPassword = null
    }

    fun resetUsername() {
        username = null
        subscriptionId = null
    }

    fun getPassword(): String? {
        return password ?: subscriptionPassword
    }

    private fun saveToken(token: String) {
        authHelper.authStatusLiveData.postValue(AuthStatus.valid)
        authHelper.tokenLiveData.postValue(token)
    }

}

enum class LoginViewModelState {
    INITIAL,
    CREDENTIALS_CHECKING,
    CREDENTIALS_INVALID,
    CREDENTIALS_MISSING,
    SUBSCRIPTION_CHECKING,
    SUBSCRIPTION_ELAPSED,
    SUBSCRIPTION_INVALID,
    SUBSCRIPTION_MISSING,
    SUBSCRIPTION_REQUEST,
    SUBSCRIPTION_TAKEN,
    PASSWORD_MISSING,
    PASSWORD_REQUESTING,
    PASSWORD_REQUESTED,
    REGISTRATION_CHECKING,
    REGISTRATION_EMAIL,
    REGISTRATION_SUCCESSFUL,
    USERNAME_MISSING,
    DONE
}