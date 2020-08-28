package de.taz.app.android.ui.login

import android.app.Application
import android.content.Context
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
import de.taz.app.android.singletons.PREFERENCES_AUTH
import de.taz.app.android.singletons.PREFERENCES_AUTH_EMAIL
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

    // save call necessary for tests
    var username: String? = application?.getSharedPreferences(
        PREFERENCES_AUTH,
        Context.MODE_PRIVATE
    )?.getString(PREFERENCES_AUTH_EMAIL, null)

    var password: String? = null
    var subscriptionId: Int? = null
    var subscriptionPassword: String? = null
    var backToArticle: Boolean = true

    var nameAffix: String? = null
    var firstName: String? = null
    var surName: String? = null
    var street: String? = null
    var city: String? = null
    var postCode: String? = null
    var country: String? = null
    var phone: String? = null
    var price: Int? = null
    var iban: String? = null
    var accountHolder: String? = null
    var comment: String? = null

    var createNewAccount: Boolean = true
    var validCredentials: Boolean = false

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
                null -> {
                    status.postValue(LoginViewModelState.INITIAL)
                    noInternet.postValue(true)
                }
                else -> {
                    toastHelper.showSomethingWentWrongToast()
                    status.postValue(LoginViewModelState.INITIAL)
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
        register(LoginViewModelState.CREDENTIALS_MISSING_FAILED)
    }

    fun getTrialSubscriptionForNewCredentials() {
        register(LoginViewModelState.SUBSCRIPTION_REQUEST_INVALID_EMAIL)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun register(
        invalidMailState: LoginViewModelState
    ): Job? {
        return runIfNotNull(this.username, this.password) { username1, password1 ->
            val previousState = status.value
            status.postValue(LoginViewModelState.LOADING)
            ioScope.launch {
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
                SubscriptionStatus.tazIdNotValid -> {
                    // should not happen
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING_FAILED)
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
                    Sentry.capture("trialSubscription returned ${subscriptionInfo?.status}")
                    toastHelper.showToast(R.string.toast_unknown_error)
                }
            }
        } catch (e: ApiService.ApiServiceException.NoInternetException) {
            status.postValue(previousState)
            noInternet.postValue(true)
        }
    }


    fun connect(): Job {
        val previousState = status.value
        status.postValue(LoginViewModelState.LOADING)
        return ioScope.launch {
            if (!createNewAccount) {
                val checkCredentials = checkCredentials()
                if (checkCredentials == false) {
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING_FAILED)
                    return@launch
                } else if (checkCredentials == null) {
                    status.postValue(previousState)
                    return@launch
                }
            }

            handleConnect(previousState)
        }
    }

    private suspend fun handleConnect(
        previousState: LoginViewModelState?
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

            log.error(subscriptionInfo.toString())
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
                SubscriptionStatus.nameTooLong,
                SubscriptionStatus.invalidMail -> {
                    resetCredentialsPassword()
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING_FAILED)
                }
                SubscriptionStatus.waitForProc -> {
                    poll()
                }
                SubscriptionStatus.waitForMail -> {
                    status.postValue(LoginViewModelState.REGISTRATION_EMAIL)
                }
                SubscriptionStatus.tazIdNotValid -> {
                    resetCredentialsPassword()
                    status.postValue(LoginViewModelState.CREDENTIALS_MISSING_FAILED)
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
                    status.postValue(
                        if (validCredentials) {
                            LoginViewModelState.SUBSCRIPTION_ALREADY_LINKED
                        } else {
                            LoginViewModelState.EMAIL_ALREADY_LINKED
                        }
                    )
                }
                null -> {
                    status.postValue(previousState)
                    noInternet.postValue(true)
                }
                else -> {
                    // should not happen
                    Sentry.capture("connect returned ${subscriptionInfo.status}")
                    toastHelper.showSomethingWentWrongToast()
                    status.postValue(previousState)
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
                SubscriptionStatus.elapsed -> {
                    status.postValue(LoginViewModelState.SUBSCRIPTION_ELAPSED)
                }
                SubscriptionStatus.invalidConnection -> {
                    status.postValue(LoginViewModelState.SUBSCRIPTION_TAKEN)
                }
                SubscriptionStatus.alreadyLinked -> {
                    status.postValue(
                        if (validCredentials) {
                            LoginViewModelState.SUBSCRIPTION_ALREADY_LINKED
                        } else {
                            LoginViewModelState.EMAIL_ALREADY_LINKED
                        }
                    )
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
                SubscriptionStatus.toManyPollTrys -> {
                    authHelper.isPolling = false
                    Sentry.capture("ToManyPollTrys")
                }
                else -> {
                    // should not happen
                    Sentry.capture("connect returned ${subscriptionInfo.status}")
                    toastHelper.showSomethingWentWrongToast()
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

    fun requestPasswordReset(subscriptionId: Boolean = false) {
        status.value?.let {
            if (it !in listOf(
                    LoginViewModelState.PASSWORD_REQUEST,
                    LoginViewModelState.PASSWORD_REQUEST_INVALID_MAIL,
                    LoginViewModelState.PASSWORD_REQUEST_INVALID_ID,
                    LoginViewModelState.PASSWORD_REQUEST_NO_MAIL,
                    LoginViewModelState.PASSWORD_REQUEST_DONE
                )
            ) {
                statusBeforePasswordRequest = status.value
            }
        }
        if (subscriptionId) {
            status.postValue(LoginViewModelState.PASSWORD_REQUEST_SUBSCRIPTION_ID)
        } else {
            status.postValue(LoginViewModelState.PASSWORD_REQUEST)
        }
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
                PasswordResetInfo.invalidMail -> {
                    status.postValue(LoginViewModelState.PASSWORD_REQUEST_INVALID_MAIL)
                }
                null,
                PasswordResetInfo.error,
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

    fun getSubscription() {
        val previousState = status.value
        status.postValue(LoginViewModelState.LOADING)
        ioScope.launch {
            try {
                ApiService.getInstance(getApplication()).subscription(
                    tazId = username ?: "",
                    idPassword = password ?: "",
                    surname = surName,
                    firstName = firstName,
                    street = street ?: "",
                    city = city ?: "",
                    postCode = postCode ?: "",
                    country = country ?: "",
                    phone = phone,
                    price = price ?: -1,
                    iban = iban ?: "",
                    accountHolder = accountHolder,
                    comment = comment, nameAffix = nameAffix
                )?.let { subscriptionInfo ->
                    log.debug("getSubscription returned: $subscriptionInfo")
                    when (subscriptionInfo.status) {
                        SubscriptionStatus.ibanNoIban -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_BANK_IBAN_EMPTY)
                        }
                        SubscriptionStatus.ibanInvalidChecksum -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_BANK_IBAN_INVALID)
                        }
                        SubscriptionStatus.ibanNoSepaCountry -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_BANK_IBAN_NO_SEPA)
                        }
                        SubscriptionStatus.invalidAccountHolder -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_BANK_ACCOUNT_HOLDER_INVALID)
                        }
                        SubscriptionStatus.invalidMail -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_ACCOUNT_MAIL_INVALID)
                        }
                        SubscriptionStatus.invalidFirstName -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_ADDRESS_FIRST_NAME_INVALID)
                        }
                        SubscriptionStatus.invalidSurname -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_ADDRESS_SURNAME_INVALID)
                        }
                        SubscriptionStatus.noFirstName -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_ADDRESS_FIRST_NAME_EMPTY)
                        }
                        SubscriptionStatus.noSurname -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_ADDRESS_SURNAME_EMPTY)
                        }
                        SubscriptionStatus.nameTooLong -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_ADDRESS_NAME_TOO_LONG)
                        }
                        SubscriptionStatus.priceNotValid -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_PRICE_INVALID)
                        }
                        SubscriptionStatus.waitForMail -> {
                            status.postValue(LoginViewModelState.REGISTRATION_EMAIL)
                        }
                        SubscriptionStatus.waitForProc -> {
                            poll()
                        }
                        SubscriptionStatus.alreadyLinked -> {
                            status.postValue(LoginViewModelState.EMAIL_ALREADY_LINKED)
                        }
                        SubscriptionStatus.tazIdNotValid -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_ACCOUNT_MAIL_INVALID)
                        }
                        SubscriptionStatus.valid,
                        SubscriptionStatus.invalidConnection,
                        SubscriptionStatus.noPollEntry,
                        SubscriptionStatus.toManyPollTrys,
                        SubscriptionStatus.subscriptionIdNotValid,
                        SubscriptionStatus.elapsed -> {
                            // this should not happen
                            Sentry.capture("subscription returned ${subscriptionInfo.status} ")
                            toastHelper.showSomethingWentWrongToast()
                            status.postValue(previousState)
                        }
                        SubscriptionStatus.invalidCity -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_ADDRESS_CITY_INVALID)
                        }
                        SubscriptionStatus.invalidCountry -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_ADDRESS_COUNTRY_INVALID)
                        }
                        SubscriptionStatus.invalidPostcode -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_ADDRESS_POSTCODE_INVALID)
                        }
                        SubscriptionStatus.invalidStreet -> {
                            status.postValue(LoginViewModelState.SUBSCRIPTION_ADDRESS_STREET_INVALID)
                        }
                    }
                } ?: run {
                    toastHelper.showSomethingWentWrongToast()
                    Sentry.capture("subscription returned null")
                    status.postValue(previousState)
                }
            } catch (nie: ApiService.ApiServiceException.NoInternetException) {
                noInternet.postValue(true)
                status.postValue(previousState)
            }
        }
    }

    private suspend fun checkCredentials(): Boolean? {
        return try {
            val authTokenInfo = apiService.authenticate(username ?: "", password ?: "")
            authTokenInfo?.authInfo?.status != AuthStatus.notValid
        } catch (e: ApiService.ApiServiceException.NoInternetException) {
            status.postValue(LoginViewModelState.INITIAL)
            noInternet.postValue(true)
            null
        }
    }


    fun requestSubscription() = ioScope.launch {
        val previousState = status.value
        status.postValue(LoginViewModelState.LOADING)
        if (!createNewAccount) {
            val checkCredentials = checkCredentials()
            if (checkCredentials == false) {
                status.postValue(LoginViewModelState.SUBSCRIPTION_ACCOUNT_INVALID)
                return@launch
            } else if (checkCredentials == null) {
                status.postValue(previousState)
                return@launch
            }
        }

        if (price == 0) {
            if (createNewAccount || !validCredentials) {
                getTrialSubscriptionForNewCredentials()
            } else {
                getTrialSubscriptionForExistingCredentials()
            }
        } else {
            getSubscription()
        }
    }
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
    PASSWORD_REQUEST_NO_MAIL,
    PASSWORD_REQUEST_INVALID_ID,
    POLLING_FAILED,
    REGISTRATION_EMAIL,
    REGISTRATION_SUCCESSFUL,
    SUBSCRIPTION_ACCOUNT,
    SUBSCRIPTION_ACCOUNT_INVALID,
    SUBSCRIPTION_ACCOUNT_MAIL_INVALID,
    SUBSCRIPTION_ADDRESS,
    SUBSCRIPTION_ADDRESS_CITY_INVALID,
    SUBSCRIPTION_ADDRESS_COUNTRY_INVALID,
    SUBSCRIPTION_ADDRESS_FIRST_NAME_EMPTY,
    SUBSCRIPTION_ADDRESS_FIRST_NAME_INVALID,
    SUBSCRIPTION_ADDRESS_SURNAME_EMPTY,
    SUBSCRIPTION_ADDRESS_SURNAME_INVALID,
    SUBSCRIPTION_ADDRESS_STREET_INVALID,
    SUBSCRIPTION_ADDRESS_NAME_TOO_LONG,
    SUBSCRIPTION_ADDRESS_POSTCODE_INVALID,
    SUBSCRIPTION_ALREADY_LINKED,
    SUBSCRIPTION_BANK,
    SUBSCRIPTION_BANK_ACCOUNT_HOLDER_INVALID,
    SUBSCRIPTION_BANK_IBAN_EMPTY,
    SUBSCRIPTION_BANK_IBAN_INVALID,
    SUBSCRIPTION_BANK_IBAN_NO_SEPA,
    SUBSCRIPTION_ELAPSED,
    SUBSCRIPTION_INVALID,
    SUBSCRIPTION_MISSING,
    SUBSCRIPTION_MISSING_INVALID_ID,
    SUBSCRIPTION_PRICE_INVALID,
    SUBSCRIPTION_REQUEST,
    SUBSCRIPTION_REQUEST_INVALID_EMAIL,
    SUBSCRIPTION_TAKEN,
    USERNAME_MISSING,
    NAME_MISSING,
    DONE
}