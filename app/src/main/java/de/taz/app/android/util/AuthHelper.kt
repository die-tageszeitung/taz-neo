package de.taz.app.android.util

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.models.AuthInfo
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.AuthTokenInfo

const val PREFERENCES_AUTH = "auth"
const val PREFERENCES_AUTH_TOKEN = "token"
const val PREFERENCES_AUTH_STATUS= "status"
const val PREFERENCES_AUTH_INSTALLATION_ID = "installation_id"

/**
 * Singleton handling authentication
 */
class AuthHelper private constructor(applicationContext: Context): ViewModel() {

    companion object : SingletonHolder<AuthHelper, Context>(::AuthHelper)

    private val preferences = applicationContext.getSharedPreferences(PREFERENCES_AUTH, Context.MODE_PRIVATE)

    var tokenLiveData = SharedPreferenceStringLiveData(preferences, PREFERENCES_AUTH_TOKEN, "")
    val token
        get() = tokenLiveData.value ?: ""

    val installationId
        get() = SharedPreferenceStringLiveData(
            preferences, PREFERENCES_AUTH_INSTALLATION_ID, ""
        ).value ?: ""

    val authStatusLiveData = SharedPreferencesAuthStatusLiveData(
        preferences, PREFERENCES_AUTH_STATUS, AuthStatus.notValid
    )

}
