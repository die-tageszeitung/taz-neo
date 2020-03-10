package de.taz.app.android

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.taz.app.android.api.ApiService
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.util.Log
import de.taz.app.android.singletons.NotificationHelper
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val REMOTE_MESSAGE_PERFOM_KEY = "perform"
const val REMOTE_MESSAGE_PERFOM_VALUE_SUBSCRIPTION_POLL = "subscriptionPoll"

class FirebaseMessagingService : FirebaseMessagingService() {

    private val log by Log

    private lateinit var authHelper: AuthHelper
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var apiService: ApiService
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        authHelper = AuthHelper.getInstance(applicationContext)
        firebaseHelper = FirebaseHelper.getInstance(applicationContext)
        apiService = ApiService.getInstance(applicationContext)
        notificationHelper = NotificationHelper.getInstance(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        log.debug("From: " + remoteMessage.from)

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            log.debug("Message data payload: " + remoteMessage.data)
            if (remoteMessage.data.containsKey(REMOTE_MESSAGE_PERFOM_KEY)) {
                when(remoteMessage.data[REMOTE_MESSAGE_PERFOM_KEY]) {
                    REMOTE_MESSAGE_PERFOM_VALUE_SUBSCRIPTION_POLL -> {
                        log.info("notification triggered subscription poll")
                        authHelper.isPolling = true
                    }
                }
            }
        }

        // Check if message contains a notification payload.
        val notification = remoteMessage.notification
        if (notification != null) {
            log.info("Notification data title: ${notification.title} body: ${notification.body}")
            notificationHelper.showNotification(notification)
        }
    }

    override fun onNewToken(token: String) {
        log.debug("new firebase messaging token: $token")

        val oldToken = firebaseHelper.firebaseToken
        firebaseHelper.firebaseToken = token
        CoroutineScope(Dispatchers.IO).launch {
            firebaseHelper.hasTokenBeenSent = apiService.sendNotificationInfo(oldToken) ?: false
            log.debug("hasTokenBeenSent set to ${firebaseHelper.hasTokenBeenSent}")
        }
    }
}
