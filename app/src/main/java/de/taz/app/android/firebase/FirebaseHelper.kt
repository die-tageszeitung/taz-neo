package de.taz.app.android.firebase

import android.content.Context
import androidx.annotation.VisibleForTesting
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class FirebaseHelper @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) constructor(
    applicationContext: Context
) {
    companion object : SingletonHolder<FirebaseHelper, Context>(::FirebaseHelper)

    private val apiService = ApiService.getInstance(applicationContext)
    private val store = FirebaseDataStore.getInstance(applicationContext)

    init {
        CoroutineScope(Dispatchers.Default).launch { ensureTokenSent() }
    }

    private suspend fun sendNotificationInfo(token: String, oldToken: String?): Boolean {
        return try {
            apiService.retryOnConnectionFailure {
                apiService.sendNotificationInfo(token, oldToken)
            }
        } catch (e: ConnectivityException.NoInternetException) {
            log.warn("Sending notification token failed because no internet available")
            false
        }
    }

    /**
     * function which will try to send the firebase token to the backend if it was not sent yet
     */
    suspend fun ensureTokenSent() {
        val token = store.token.get()
        if (token.isNullOrEmpty()) {
            log.verbose("No Token has been set yet")
            return
        }

        if (store.tokenSent.get()) {
            log.verbose("Token has already been sent: $token")
            return
        }

        val sent = sendNotificationInfo(token, store.oldToken.get())

        // If the backend call is successful, the oldToken can be released
        if (sent) {
            store.tokenSent.set(true)
            store.oldToken.set(null)
        }
    }

    /**
     * function to call when a new Firebase token exists
     * @param newToken - the new token
     */
    fun updateToken(newToken: String) = CoroutineScope(Dispatchers.Default).launch {
        // Store the current token as the oldToken and pass it to the server
        // If there is already a pending oldToken (!=null) that has not been sent to the server,
        // we have to keep that.
        if (store.oldToken.get() == null) {
            store.oldToken.set(store.token.get())
        }
        // Ensure that the newToken will be sent to the server
        store.tokenSent.set(false)
        store.token.set(newToken)
        ensureTokenSent()
    }
}