package de.taz.app.android.util

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.models.AuthTokenInfo

/**
 * Singleton handling authentication
 */
class AuthHelper private constructor(applicationContext: Context): ViewModel() {

    companion object : SingletonHolder<AuthHelper, Context>(::AuthHelper)

    private val preferences = applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE)

    var tokenLiveData: MutableLiveData<String> = MutableLiveData<String>().apply { token }
        private set

    var token: String
        set (value) {
            preferences.edit().putString("token", value).apply()
            tokenLiveData.value = token
        }
        get() = preferences.getString("token", "") ?: ""


    var authTokenInfo = MutableLiveData<AuthTokenInfo?>().apply { value = null }

    init {
        authTokenInfo.observeForever { authTokenInfo ->
            if (!authTokenInfo?.token.isNullOrBlank()) {
                token = authTokenInfo?.token ?: ""
            }
        }
    }

}