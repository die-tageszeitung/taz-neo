package de.taz.app.android.firebase

import android.content.Context
import androidx.annotation.VisibleForTesting
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext


@Mockable
class FirebaseHelper @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) constructor(
    applicationContext: Context
): CoroutineScope {
    companion object : SingletonHolder<FirebaseHelper, Context>(::FirebaseHelper)

    private val apiService = ApiService.getInstance(applicationContext)
    private val store = FirebaseDataStore.getInstance(applicationContext)

    init {
        ensureTokenSent()
    }

    private suspend fun sendNotificationInfo(
    ): Boolean {
        val token = store.token.get()
        val oldToken = store.oldToken.get()

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
    final fun ensureTokenSent() = launch { ensureTokenSentSus() }
    private suspend fun ensureTokenSentSus() {
        try {
            if (!store.tokenSent.get() && !store.token.get().isNullOrEmpty()) {
                val sent = sendNotificationInfo()
                store.tokenSent.set(sent)
                log.debug("hasTokenBeenSent set to $sent")

                if (sent) {
                    store.oldToken.set(null)
                }
            } else {
                log.info("token already set")
            }
        } catch (e: ConnectivityException.NoInternetException) {
            log.warn("Sending notification token failed because no internet available")
        }
    }

    /**
     * function to call when a new Firebase token exists
     * @param newToken - the new token
     */
    fun updateToken(newToken: String) = CoroutineScope(Dispatchers.IO).launch {
        store.oldToken.set(store.token.get())
        store.token.set(newToken)
        ensureTokenSentSus()
    }

    override val coroutineContext: CoroutineContext = SupervisorJob()
}