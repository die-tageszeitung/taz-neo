package de.taz.app.android.singletons

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.util.*

const val PREFERENCES_AUTH = "auth"
const val PREFERENCES_AUTH_TOKEN = "token"
const val PREFERENCES_AUTH_STATUS = "status"
const val PREFERENCES_AUTH_INSTALLATION_ID = "installation_id"

/**
 * Singleton handling authentication
 */
class AuthHelper private constructor(applicationContext: Context) : ViewModel() {

    companion object : SingletonHolder<AuthHelper, Context>(::AuthHelper)

    private val preferences =
        applicationContext.getSharedPreferences(PREFERENCES_AUTH, Context.MODE_PRIVATE)

    private var tokenLiveData =
        SharedPreferenceStringLiveData(
            preferences,
            PREFERENCES_AUTH_TOKEN,
            ""
        )
    var token
        get() = tokenLiveData.value ?: ""
        set(value) = tokenLiveData.postValue(value)

    /**
     * function to observe Authentication Token
     * @return the generated Observer so it can be removed
     */
    fun observeToken(
        lifecycleOwner: LifecycleOwner,
        observationCallback: (String) -> Unit
    ): Observer<String> {
        return observe(tokenLiveData, lifecycleOwner, observationCallback)
    }

    val installationId
        get() = SharedPreferenceStringLiveData(
            preferences, PREFERENCES_AUTH_INSTALLATION_ID, ""
        ).value ?: ""


    private val authStatusLiveData =
        SharedPreferencesAuthStatusLiveData(
            preferences, PREFERENCES_AUTH_STATUS, AuthStatus.notValid
        )

    var authStatus: AuthStatus
        get() = authStatusLiveData.value ?: AuthStatus.notValid
        set(value) = authStatusLiveData.postValue(value)

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

}
