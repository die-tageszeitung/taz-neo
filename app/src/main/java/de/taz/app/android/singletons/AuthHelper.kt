package de.taz.app.android.singletons

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.util.*

const val PREFERENCES_AUTH = "auth"
const val PREFERENCES_AUTH_EMAIL = "email"
const val PREFERENCES_AUTH_INSTALLATION_ID = "installation_id"
const val PREFERENCES_AUTH_POLL = "poll"
const val PREFERENCES_AUTH_STATUS = "status"
const val PREFERENCES_AUTH_TOKEN = "token"

/**
 * Singleton handling authentication
 */
@Mockable
class AuthHelper private constructor(applicationContext: Context) : ViewModel() {

    companion object : SingletonHolder<AuthHelper, Context>(::AuthHelper)

    private val preferences =
        applicationContext.getSharedPreferences(PREFERENCES_AUTH, Context.MODE_PRIVATE)

    var tokenLiveData =
        SharedPreferenceStringLiveData(
            preferences,
            PREFERENCES_AUTH_TOKEN,
            ""
        )
    var token
        get() = tokenLiveData.value ?: ""
        set(value) = tokenLiveData.postValue(value)

    val installationId
        get() = SharedPreferenceStringLiveData(
            preferences, PREFERENCES_AUTH_INSTALLATION_ID, ""
        ).value ?: ""


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var authStatusLiveData: MutableLiveData<AuthStatus> =
        SharedPreferencesAuthStatusLiveData(
            preferences, PREFERENCES_AUTH_STATUS, AuthStatus.notValid
        )

    var authStatus: AuthStatus
        get() = authStatusLiveData.value ?: AuthStatus.notValid
        set(value) = authStatusLiveData.postValue(value)

    fun isLoggedIn(): Boolean = authStatus == AuthStatus.valid

    /**
     * function to observe AuthStatus
     * @return the generated Observer so it can be removed
     */
    fun observeAuthStatus(
        lifecycleOwner: LifecycleOwner,
        observationCallback: (AuthStatus) -> Unit
    ): Observer<AuthStatus> {
        return observe(authStatusLiveData, lifecycleOwner, observationCallback)
    }
    fun observeAuthStatusOnce(
        lifecycleOwner: LifecycleOwner,
        observationCallback: (AuthStatus) -> Unit
    ): Observer<AuthStatus> {
        return observeOnce(authStatusLiveData, lifecycleOwner, observationCallback)
    }

    private val emailLiveData = SharedPreferenceStringLiveData(
        preferences, PREFERENCES_AUTH_EMAIL, ""
    )

    var email
        get() = emailLiveData.value
        set(value) = emailLiveData.postValue(value)

    fun observeEmail(
        lifecycleOwner: LifecycleOwner,
        observationCallback: (String) -> Unit
    ): Observer<String> {
        return observe(emailLiveData, lifecycleOwner, observationCallback)
    }

    private val pollingLiveData = SharedPreferenceBooleanLiveData(
        preferences, PREFERENCES_AUTH_POLL, false
    )

    var isPolling: Boolean
        get() = pollingLiveData.value ?: false
        set(value) = pollingLiveData.postValue(value)

    fun observeIsPolling(
        lifecycleOwner: LifecycleOwner,
        observationCallback: (Boolean) -> Unit
    ): Observer<Boolean> {
        return observe(pollingLiveData, lifecycleOwner, observationCallback)
    }

}
