package de.taz.app.android

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.taz.app.android.api.ApiService
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.ui.splash.CHANNEL_ID_DEBUG
import de.taz.app.android.util.Log
import de.taz.app.android.util.NotificationHelper
import de.taz.app.android.util.ToastHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FirebaseMessagingService : FirebaseMessagingService() {

    private val log by Log
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var apiService: ApiService
    private val notificationHelper = NotificationHelper.getInstance()

    override fun onCreate() {
        super.onCreate()
        firebaseHelper = FirebaseHelper.getInstance(applicationContext)
        apiService = ApiService.getInstance(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        log.debug("From: " + remoteMessage.from)

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            notificationHelper.showNotification(
                "data payload",
                remoteMessage.data.toString(),
                CHANNEL_ID_DEBUG,
                bigText = true
            )
            log.debug("Message data payload: " + remoteMessage.data)
        }

        // Check if message contains a notification payload.
        val notification = remoteMessage.notification
        if (notification != null) {
            notificationHelper.showNotification(
                notification.title ?: "no title",
                notification.body ?: "no body",
                CHANNEL_ID_DEBUG,
                bigText = true
            )

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
