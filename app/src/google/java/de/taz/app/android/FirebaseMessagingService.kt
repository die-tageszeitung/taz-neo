package de.taz.app.android

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.taz.app.android.api.ApiService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.NotificationHelper
import de.taz.app.android.singletons.SETTINGS_DOWNLOAD_ENABLED
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val REMOTE_MESSAGE_PERFORM_KEY = "perform"
const val REMOTE_MESSAGE_PERFORM_VALUE_SUBSCRIPTION_POLL = "subscriptionPoll"
const val REMOTE_MESSAGE_REFRESH_KEY = "refresh"
const val REMOTE_MESSAGE_REFRESH_VALUE_ABO_POLL = "aboPoll"

class FirebaseMessagingService : FirebaseMessagingService() {

    private val log by Log

    private lateinit var apiService: ApiService
    private lateinit var authHelper: AuthHelper
    private lateinit var downloadService: DownloadService
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var issueRepository: IssueRepository
    private lateinit var notificationHelper: NotificationHelper

    private val messageTimestamps: MutableList<Long> = mutableListOf()

    override fun onCreate() {
        super.onCreate()
        apiService = ApiService.getInstance(applicationContext)
        authHelper = AuthHelper.getInstance(applicationContext)
        downloadService = DownloadService.getInstance(applicationContext)
        firebaseHelper = FirebaseHelper.getInstance(applicationContext)
        issueRepository = IssueRepository.getInstance(applicationContext)
        notificationHelper = NotificationHelper.getInstance(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {

        // sometimes messages are sent multiple times - only execute once
        val sentTime = remoteMessage.sentTime
        if (sentTime !in messageTimestamps) {
            messageTimestamps.add(sentTime)

            log.debug("From: " + remoteMessage.from)

            Sentry.capture("Notification received - from: ${remoteMessage.from} data: ${remoteMessage.data}")

            // Check if message contains a data payload.
            if (remoteMessage.data.isNotEmpty()) {
                log.debug("Message data payload: " + remoteMessage.data)
                if (remoteMessage.data.containsKey(REMOTE_MESSAGE_PERFORM_KEY)) {
                    when (remoteMessage.data[REMOTE_MESSAGE_PERFORM_KEY]) {
                        REMOTE_MESSAGE_PERFORM_VALUE_SUBSCRIPTION_POLL -> {
                            log.info("notification triggered $REMOTE_MESSAGE_PERFORM_VALUE_SUBSCRIPTION_POLL")
                            authHelper.isPolling = true
                        }
                    }
                }
                if (remoteMessage.data.containsKey(REMOTE_MESSAGE_REFRESH_KEY)) {
                    when (remoteMessage.data[REMOTE_MESSAGE_REFRESH_KEY]) {
                        REMOTE_MESSAGE_REFRESH_VALUE_ABO_POLL -> {
                            log.info("notification triggered $REMOTE_MESSAGE_REFRESH_VALUE_ABO_POLL")
                            CoroutineScope(Dispatchers.IO).launch {
                                val issue = apiService.getLastIssues(1).first()
                                issueRepository.save(issue)
                                val downloadPreferences = applicationContext.getSharedPreferences(
                                    PREFERENCES_DOWNLOADS,
                                    Context.MODE_PRIVATE
                                )
                                if (downloadPreferences.getBoolean(SETTINGS_DOWNLOAD_ENABLED, true)) {
                                    downloadService.scheduleDownload(issue)
                                }
                            }
                        }
                    }
                }
            }

            // Check if message contains a notification payload.
            val notification = remoteMessage.notification
            if (notification != null) {
                log.info("Notification data title: ${notification.title} body: ${notification.body}")
                showNotification(notification)
            }
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

    private fun showNotification(notification: RemoteMessage.Notification) {
        notification.apply {
            runIfNotNull(title, body) { title, body ->
                notificationHelper.showNotification(
                    title,
                    body,
                    applicationContext.getString(R.string.notification_fcm_channel_id)
                )
            }
        }
    }

}
