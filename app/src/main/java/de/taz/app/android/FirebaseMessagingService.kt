package de.taz.app.android

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.taz.app.android.api.ApiService
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FirebaseMessagingService : FirebaseMessagingService() {

    private val log by Log
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var apiService: ApiService

    override fun onCreate() {
        super.onCreate()
        firebaseHelper = FirebaseHelper.getInstance(applicationContext)
        apiService = ApiService.getInstance(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        log.debug("From: " + remoteMessage.from)

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            ToastHelper.getInstance()
                .makeToast("Message Notification payload: ${remoteMessage.data}")
            log.debug("Message data payload: " + remoteMessage.data)
        }

        // Check if message contains a notification payload.
        val notification = remoteMessage.notification
        if (notification != null) {
            ToastHelper.getInstance().makeToast("Message Notification Title: ${notification.title}")
            ToastHelper.getInstance().makeToast("Message Notification Body: ${notification.body}")
        }
    }

    override fun onNewToken(token: String) {
        log.debug("new firebase messaging token")
        firebaseHelper.firebaseToken = token
        CoroutineScope(Dispatchers.IO).launch {
            firebaseHelper.hasTokenBeenSent = apiService.sendNotificationInfo() ?: false
            log.debug("hasTokenBeenSent set to ${firebaseHelper.hasTokenBeenSent}")
        }
    }
}
