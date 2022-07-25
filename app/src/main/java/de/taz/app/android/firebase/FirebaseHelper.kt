package de.taz.app.android.firebase

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.dataStore.MappingDataStoreEntry
import de.taz.app.android.dataStore.SimpleDataStoreEntry
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext


// region old setting names
private const val PREFERENCES_FCM = "fcm"
// endregion

// region setting keys
private const val FCM_TOKEN = "fcm token"
private const val FCM_TOKEN_OLD = "fcm token old"
private const val FCM_TOKEN_SENT = "fcm token sent"
// endregion

private val Context.fcmDataStore: DataStore<Preferences> by preferencesDataStore(
    PREFERENCES_FCM,
    produceMigrations = {
        listOf(
            SharedPreferencesMigration(
                it,
                PREFERENCES_FCM
            ),
        )
    }
)

@Mockable
class FirebaseHelper @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) constructor(
    applicationContext: Context
) : CoroutineScope {

    companion object : SingletonHolder<FirebaseHelper, Context>(::FirebaseHelper)

    private val dataStore = applicationContext.fcmDataStore
    private val apiService = ApiService.getInstance(applicationContext)

    init {
        ensureTokenSent()
    }

    // we need the token and oldToken to be null if it is empty
    // otherwise the graphql endpoint has a problem
    val token = MappingDataStoreEntry<String?, String>(
        dataStore,
        stringPreferencesKey(FCM_TOKEN),
        "",
        { it ?: "" },
        { it.takeIf { it.isNotEmpty() } }
    )
    val oldToken = MappingDataStoreEntry<String?, String>(
        dataStore,
        stringPreferencesKey(FCM_TOKEN_OLD),
        "",
        { it ?: "" },
        { it.takeIf { it.isNotEmpty() } }
    )
    val tokenSent = SimpleDataStoreEntry(dataStore, booleanPreferencesKey(FCM_TOKEN_SENT), false)

    suspend fun isPush(): Boolean = token.get()?.isNotEmpty() ?: false

    private suspend fun sendNotificationInfo(
    ): Boolean {
        val token = token.get()
        val oldToken = oldToken.get()

        return if (token != null) {
            log.info("Sending notification info")
            apiService.retryOnConnectionFailure {
                apiService.sendNotificationInfo(token, oldToken)
            }
        } else {
            false
        }
    }

    /**
     * function which will try to send the firebase token to the backend if it was not sent yet
     */
    fun ensureTokenSent() = launch { ensureTokenSentSus() }
    private suspend fun ensureTokenSentSus() {
        try {
            if (!tokenSent.get() && !token.get().isNullOrEmpty()) {
                val sent = sendNotificationInfo()
                tokenSent.set(sent)
                log.debug("hasTokenBeenSent set to $sent")

                if (sent) {
                    oldToken.set(null)
                }
            }
        } catch (e: ConnectivityException.NoInternetException) {
            log.warn("Sending notification token failed because no internet available")
        }
    }

    /**
     * function to call when a new Firebase token exists
     * @param newToken - the new token
     */
    fun updateToken(newToken: String) = launch {
        oldToken.set(token.get())
        token.set(newToken)
        ensureTokenSentSus()
    }

    // region CoroutineScope
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO
    // endregion
}