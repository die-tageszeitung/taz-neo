package de.taz.app.android

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.taz.app.android.data.DownloadScheduler
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.NotificationHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val REMOTE_MESSAGE_PERFORM_KEY = "perform"
private const val REMOTE_MESSAGE_PERFORM_VALUE_SUBSCRIPTION_POLL = "subscriptionPoll"
private const val REMOTE_MESSAGE_REFRESH_KEY = "refresh"
private const val REMOTE_MESSAGE_REFRESH_VALUE_ABO_POLL = "aboPoll"

// Do avoid DDoSing the taz servers on a new issue release we add a randomized delay. The randomized
// delay ranges from 0 to DOWNLOAD_DELAY_MAX_MS
private const val DOWNLOAD_DELAY_MAX_MS = 3600000L // 1 hour


class FirebaseMessagingService : FirebaseMessagingService() {

    private val log by Log

    private lateinit var authHelper: AuthHelper
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var issueRepository: IssueRepository
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var downloadScheduler: DownloadScheduler

    private val messageTimestamps: MutableList<Long> = mutableListOf()

    override fun onCreate() {
        super.onCreate()
        authHelper = AuthHelper.getInstance(applicationContext)
        firebaseHelper = FirebaseHelper.getInstance(applicationContext)
        issueRepository = IssueRepository.getInstance(applicationContext)
        notificationHelper = NotificationHelper.getInstance(applicationContext)
        downloadScheduler = DownloadScheduler.getInstance(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // sometimes messages are sent multiple times - only execute once
        val sentTime = remoteMessage.sentTime
        if (sentTime !in messageTimestamps) {
            messageTimestamps.add(sentTime)

            log.debug("From: " + remoteMessage.from)

            // Check if message contains a data payload.
            if (remoteMessage.data.isNotEmpty()) {
                log.debug("Message data payload: " + remoteMessage.data)
                if (remoteMessage.data.containsKey(REMOTE_MESSAGE_PERFORM_KEY)) {
                    when (remoteMessage.data[REMOTE_MESSAGE_PERFORM_KEY]) {
                        REMOTE_MESSAGE_PERFORM_VALUE_SUBSCRIPTION_POLL -> {
                            log.info("notification triggered $REMOTE_MESSAGE_PERFORM_VALUE_SUBSCRIPTION_POLL")
                            CoroutineScope(Dispatchers.Default).launch {
                                authHelper.isPolling.set(true)
                            }
                        }
                    }
                }
                if (remoteMessage.data.containsKey(REMOTE_MESSAGE_REFRESH_KEY)) {
                    when (remoteMessage.data[REMOTE_MESSAGE_REFRESH_KEY]) {
                        REMOTE_MESSAGE_REFRESH_VALUE_ABO_POLL -> {
                            log.info("notification triggered $REMOTE_MESSAGE_REFRESH_VALUE_ABO_POLL")
                            downloadNewestIssue(
                                remoteMessage.sentTime,
                                delay = Random.nextLong(0, DOWNLOAD_DELAY_MAX_MS)
                            )
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

    private fun downloadNewestIssue(sentTime: Long, delay: Long = 0) {
        CoroutineScope(Dispatchers.Default).launch {
            if (DownloadDataStore.getInstance(applicationContext).enabled.get()) {
                downloadScheduler.scheduleNewestIssueDownload(sentTime.toString(), delay = delay)
            }
        }
    }

    override fun onNewToken(token: String) {
        log.debug("new firebase messaging token: $token")
        firebaseHelper.updateToken(token)
    }

    private fun showNotification(notification: RemoteMessage.Notification) {
        notification.apply {
            runIfNotNull(title, body) { title, body ->
                notificationHelper.showNotification(
                    title,
                    body,
                    applicationContext.getString(R.string.notification_channel_fcm_new_issue_arrived_id)
                )
            }
        }
    }
}
