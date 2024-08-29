package de.taz.app.android

import android.app.PendingIntent
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.Issue
import de.taz.app.android.content.ContentService
import de.taz.app.android.data.DownloadScheduler
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.NotificationHelper
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.splash.SplashActivity
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random


private const val REMOTE_MESSAGE_PERFORM_KEY = "perform"
private const val REMOTE_MESSAGE_PERFORM_VALUE_SUBSCRIPTION_POLL = "subscriptionPoll"
private const val REMOTE_MESSAGE_REFRESH_KEY = "refresh"
private const val REMOTE_MESSAGE_REFRESH_VALUE_ABO_POLL = "aboPoll"
private const val REMOTE_MESSAGE_ARTICLE_MEDIA_SYNC_ID = "articleMsId"
private const val REMOTE_MESSAGE_ARTICLE_DATE = "articleDate"
private const val REMOTE_MESSAGE_ARTICLE_TITLE = "articleTitle"
private const val REMOTE_MESSAGE_ARTICLE_BODY = "articleBody"


private const val WAITING_TIME_UNTIL_FALLBACK_NOTIFICATION_WILL_BE_SHOWN = 19000L

// Do avoid DDoSing the taz servers on a new issue release we add a randomized delay. The randomized
// delay ranges from 0 to DOWNLOAD_DELAY_MAX_MS
private const val DOWNLOAD_DELAY_MAX_MS = 3600000L // 1 hour


class FirebaseMessagingService : FirebaseMessagingService() {

    private val log by Log

    private lateinit var articleRepository: ArticleRepository
    private lateinit var authHelper: AuthHelper
    private lateinit var contentService: ContentService
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var issueRepository: IssueRepository
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var downloadScheduler: DownloadScheduler

    private val messageTimestamps: MutableList<Long> = mutableListOf()
    private var silentArticleNotificationHandled = false

    override fun onCreate() {
        super.onCreate()
        articleRepository = ArticleRepository.getInstance(applicationContext)
        authHelper = AuthHelper.getInstance(applicationContext)
        contentService = ContentService.getInstance(applicationContext)
        firebaseHelper = FirebaseHelper.getInstance(applicationContext)
        generalDataStore = GeneralDataStore.getInstance(applicationContext)
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
                if (remoteMessage.data.containsKey(REMOTE_MESSAGE_ARTICLE_MEDIA_SYNC_ID) &&
                    remoteMessage.data.containsKey(REMOTE_MESSAGE_ARTICLE_DATE)
                ) {
                    silentArticleNotificationHandled = false

                    val mediaSyncId = remoteMessage.data.getValue(REMOTE_MESSAGE_ARTICLE_MEDIA_SYNC_ID)
                    val articleDate = remoteMessage.data.getValue(REMOTE_MESSAGE_ARTICLE_DATE)

                    CoroutineScope(Dispatchers.Default).launch {
                        handleArticleToNotification(mediaSyncId, articleDate)
                        // After 20 seconds silent push will not be handled anymore,
                        // so we show a fallback notification (without proper article) then.
                        delay(WAITING_TIME_UNTIL_FALLBACK_NOTIFICATION_WILL_BE_SHOWN)
                        if (!silentArticleNotificationHandled) {
                            silentArticleNotificationHandled = true
                            try {
                                val title = remoteMessage.data.getValue(REMOTE_MESSAGE_ARTICLE_TITLE)
                                val body = remoteMessage.data.getValue(REMOTE_MESSAGE_ARTICLE_BODY)
                                showArticleNotification(title, body)
                                SentryWrapper.captureMessage("Show fallback article notification")
                            } catch (e: NoSuchElementException) {
                                log.warn("Could not show fallback notification", e)
                                SentryWrapper.captureException(e)
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

    private fun showArticleNotification(title: String, body: String) {
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(this, SplashActivity::class.java),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notificationHelper.showNotification(
            title = title,
            body = body,
            bigText = true,
            channelId = applicationContext.getString(R.string.notification_channel_fcm_special_article_id),
            pendingIntent = pendingIntent,
        )
    }

    /**
     * We try to get the [ArticleStub] from the given [mediaSyncId].
     * If we don't get it, we need to download the issues metadata first.
     * Then we can call [generateArticleNotification] with the [ArticleStub] and [IssueKey]
     */
    private suspend fun handleArticleToNotification(mediaSyncId: String, articleDate: String) {
        val articleStub = articleRepository.getStubByMediaSyncId(mediaSyncId)

        if (articleStub == null) {
            val issue = downloadIssueMetadata(articleDate)
            val article =
                issue?.getArticles()?.firstOrNull { it.mediaSyncId.toString() == mediaSyncId }
            if (article == null) {
                val message = "Could not download issue and fetch article for articleMediaSyncId $mediaSyncId"
                log.warn(message)
                SentryWrapper.captureMessage(message)
            } else {
                generateArticleNotification(ArticleStub(article), issue.issueKey)
            }
        } else {
            val issueKey =
                issueRepository.getIssueStubForArticle(articleStub.articleFileName)?.issueKey
            if (issueKey == null) {
                log.warn("Could not fetch issueKey")
            } else {
                generateArticleNotification(articleStub, issueKey)
            }
        }
    }

    private suspend fun downloadIssueMetadata(articleDate: String): Issue? {
        return try {
            val issuePublication = IssuePublication(BuildConfig.DISPLAYED_FEED, articleDate)
            contentService.downloadMetadata(issuePublication, maxRetries = 5, allowCache = true) as Issue?
        } catch (e: Exception) {
            log.warn("Error while trying to download metadata of issue publication of $articleDate",e)
            SentryWrapper.captureException(e)
            null
        }
    }

    /**
     * Build an intent with the proper [IssuePublication] or [IssuePublicationWithPages],
     * make it a [PendingIntent] and use [NotificationHelper] to build and show a notification.
     */
    private suspend fun generateArticleNotification(
        articleStub: ArticleStub,
        issueKey: IssueKey,
    ) {
        val isPdfMode = generalDataStore.pdfMode.get()
        val isInitComplete = (application as AbstractTazApplication).isInitComplete

        val intent = if (isInitComplete) {
            if (isPdfMode) {
                MainActivity.newIntent(
                    applicationContext,
                    IssuePublicationWithPages(issueKey),
                    articleStub.key
                )
            } else {
                MainActivity.newIntent(
                    applicationContext,
                    IssuePublication(issueKey),
                    articleStub.key
                )
            }
        } else {
            if (isPdfMode) {
                SplashActivity.newIntent(
                    applicationContext,
                    IssuePublicationWithPages(issueKey),
                    articleStub.key
                )
            } else {
                SplashActivity.newIntent(
                    applicationContext,
                    IssuePublication(issueKey),
                    articleStub.key
                )
            }
        }
        intent.addFlags(FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_NEW_TASK)

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        runIfNotNull(articleStub.title, articleStub.teaser) { title, teaser ->
            if (!silentArticleNotificationHandled) {
                silentArticleNotificationHandled = true
                notificationHelper.showNotification(
                    title = title,
                    body = teaser,
                    bigText = true,
                    channelId = applicationContext.getString(R.string.notification_channel_fcm_special_article_id),
                    pendingIntent = pendingIntent,
                )
            }
        }
    }
}
