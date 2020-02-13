package de.taz.app.android.ui.login

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.SubscriptionStatus
import de.taz.app.android.util.AuthHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginViewModel(
    initialUsername: String? = null,
    initialPassword: String? = null,
    private val apiService: ApiService = ApiService.getInstance(),
    private val authHelper: AuthHelper = AuthHelper.getInstance()
) : ViewModel() {

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
                                AuthStatus.tazIdNotLinked ->
                                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING)
                                AuthStatus.elapsed ->
                                    status.postValue(LoginViewModelState.SUBSCRIPTION_ELAPSED)
                                AuthStatus.notValid -> {
                                    password = null
                                    status.postValue(LoginViewModelState.SUBSCRIPTION_INVALID)
                                }
                                AuthStatus.valid -> {
                                    status.postValue(LoginViewModelState.USE_CREDENTIALS)
                                }
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
        // TODO
        username?.let { username ->
            password?.let { password ->
                status.postValue(LoginViewModelState.REGISTRATION_CHECKING)

                CoroutineScope(Dispatchers.IO).launch {
                    val subscriptionInfo = apiService.trialSubscription(username, password)

                    when (subscriptionInfo?.status) {
                        SubscriptionStatus.aboIdNotValid -> {
                        }
                        SubscriptionStatus.tazIdNotValid -> {
                        }
                        SubscriptionStatus.alreadyLinked -> {
                        }
                        SubscriptionStatus.elapsed -> {
                        }
                        SubscriptionStatus.invalidConnection -> {
                        }
                        SubscriptionStatus.noPollEntry -> {
                        }
                        SubscriptionStatus.valid -> {
                            saveToken(subscriptionInfo.token!!)
                            status.postValue(LoginViewModelState.REGISTRATION_SUCCESSFUL)
                        }
                        SubscriptionStatus.waitForEmail -> {
                            status.postValue(LoginViewModelState.REGISTRATION_EMAIL)
                        }
                        SubscriptionStatus.waitForProc -> {
                            // TODO wait a period of time
                            poll()
                        }
                        else -> {
                        }
                    }
                }

            }
        }
    }

    private fun poll() {
        status.postValue(LoginViewModelState.REGISTRATION_CHECKING)

        CoroutineScope(Dispatchers.IO).launch {
            val subscriptionPoll = apiService.subscriptionPoll()
            when (subscriptionPoll?.status) {
                SubscriptionStatus.valid -> {
                }
                SubscriptionStatus.tazIdNotValid -> {
                }
                SubscriptionStatus.aboIdNotValid -> {
                }
                SubscriptionStatus.elapsed -> {
                }
                SubscriptionStatus.invalidConnection -> {
                }
                SubscriptionStatus.alreadyLinked -> {
                }
                SubscriptionStatus.waitForEmail -> {
                }
                SubscriptionStatus.waitForProc -> {
                }
                SubscriptionStatus.noPollEntry -> {
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
    PASSWORD_MISSING,
    REGISTRATION_CHECKING,
    REGISTRATION_EMAIL,
    REGISTRATION_SUCCESSFUL,
    USERNAME_MISSING,
    USE_CREDENTIALS,
    DONE
}