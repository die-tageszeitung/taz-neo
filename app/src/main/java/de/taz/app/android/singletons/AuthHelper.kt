package de.taz.app.android.singletons

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    private val toastHelper = ToastHelper.getInstance(applicationContext)

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

    var authStatusLiveData: MutableLiveData<AuthStatus> =
        SharedPreferencesAuthStatusLiveData(
            preferences, PREFERENCES_AUTH_STATUS, AuthStatus.notValid
        )
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) set

    var authStatus: AuthStatus
        get() = authStatusLiveData.value ?: AuthStatus.notValid
        set(value) = authStatusLiveData.postValue(value)

    fun isLoggedIn(): Boolean = authStatus == AuthStatus.valid

    val emailLiveData = SharedPreferenceStringLiveData(
        preferences, PREFERENCES_AUTH_EMAIL, ""
    )

    var email
        get() = emailLiveData.value
        set(value) = emailLiveData.postValue(value)

    val isPollingLiveData = SharedPreferenceBooleanLiveData(
        preferences, PREFERENCES_AUTH_POLL, false
    )

    var isPolling: Boolean
        get() = isPollingLiveData.value ?: false
        set(value) = isPollingLiveData.postValue(value)

    init {
        CoroutineScope(Dispatchers.Main).launch {
            authStatusLiveData.observeDistinctIgnoreFirst(ProcessLifecycleOwner.get()) { authStatus ->
                if (authStatus == AuthStatus.elapsed) {
                    toastHelper.showToast(R.string.toast_logout_elapsed)
                    IssueRepository.getInstance(applicationContext).deleteNotDownloadedRegularIssues()
                }
                if (authStatus == AuthStatus.notValid) {
                    toastHelper.showToast(R.string.toast_logout_invalid)
                    IssueRepository.getInstance(applicationContext).deleteNotDownloadedRegularIssues()
                }
                if (authStatus == AuthStatus.valid) {
                    CoroutineScope(Dispatchers.IO).launch {
                        ApiService.getInstance(applicationContext).sendNotificationInfoAsync()
                    }
                }
            }
        }
    }

}
