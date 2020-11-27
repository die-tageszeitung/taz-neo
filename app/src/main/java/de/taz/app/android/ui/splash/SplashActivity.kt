package de.taz.app.android.ui.splash

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.*
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.*
import de.taz.app.android.base.BaseActivity
import de.taz.app.android.data.DataService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.download.IssueDownloadWorkManagerWorker
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.util.Log
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.DataPolicyActivity
import de.taz.app.android.ui.START_HOME_ACTIVITY
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.home.page.HomePageViewModel
import io.sentry.core.Sentry
import io.sentry.core.protocol.User
import kotlinx.coroutines.*
import java.util.*
import kotlin.Exception

const val CHANNEL_ID_NEW_VERSION = "NEW_VERSION"
const val NEW_VERSION_REQUEST_CODE = 0

class SplashActivity : BaseActivity() {

    private val log by Log

    private lateinit var dataService: DataService
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var authHelper: AuthHelper
    private lateinit var toastHelper: ToastHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataService = DataService.getInstance()
        firebaseHelper = FirebaseHelper.getInstance()
        authHelper = AuthHelper.getInstance()
        toastHelper = ToastHelper.getInstance()
    }

    override fun onResume() {
        super.onResume()
        log.info("splashactivity onresume called")

        CoroutineScope(Dispatchers.IO).launch {
            launch { checkAppVersion() }
            launch { checkForNewestIssue() }
            launch { sendPushToken() }
        }
        lifecycleScope.launch {
            setupSentry()

            generateInstallationId()
            generateNotificationChannels()
            val initJob = launch {
                launch { ensureAppInfo() }
                launch { initResources() }
                launch { initFeedsAndFirstIssues() }
            }
            try {
                initJob.join()
            } catch (e: InitializationException) {
                toastHelper.showSomethingWentWrongToast()
                Sentry.captureException(e)
                finish()
                return@launch
            }

            if (isDataPolicyAccepted()) {
                if (isFirstTimeStart()) {
                    val intent = Intent(this@SplashActivity, WelcomeActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    intent.putExtra(START_HOME_ACTIVITY, true)
                    startActivity(intent)
                } else {
                    val intent = Intent(this@SplashActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    startActivity(intent)
                }
            } else {
                val intent = Intent(this@SplashActivity, DataPolicyActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                startActivity(intent)
            }
            finish()
        }
    }

    private suspend fun initFeedsAndFirstIssues() {
        try {
            val feed = dataService.getFeedByName(DISPLAYED_FEED)
            feed?.let {
                dataService.getIssueStubsByFeed(Date(), listOf(it.name), 3)
            } ?: run {
                val hint = "Could not retrieve $DISPLAYED_FEED Feed"
                log.error(hint)
                throw InitializationException(hint)
            }
        } catch (e: ConnectivityException.NoInternetException) {
            log.warn("Failed to retrieve feed and first issues during startup, no internet")
        }
    }

    private suspend fun checkForNewestIssue() {
        try {
            val oldFeed = dataService.getFeedByName(DISPLAYABLE_NAME)
            val feed = dataService.getFeedByName(DISPLAYED_FEED, allowCache = false)
            // determine whether a new issue was published
            if (oldFeed?.publicationDates?.get(0) != feed?.publicationDates?.get(0)) {
                // download that new issue
                feed?.let {
                    val issueStub = dataService.getIssueStubsByFeed(
                        it.publicationDates[0],
                        listOf(it.name),
                        1,
                        allowCache = false
                    ).firstOrNull()
                    issueStub?.getIssue()?.let { issue -> dataService.ensureDownloaded(issue) }
                }
            }
        } catch (e: ConnectivityException.Recoverable) {
            toastHelper.showNoConnectionToast()
        }
    }

    private fun generateInstallationId() {
        val preferences =
            applicationContext.getSharedPreferences(PREFERENCES_AUTH, Context.MODE_PRIVATE)

        val installationId = preferences.getString(PREFERENCES_AUTH_INSTALLATION_ID, null)
        installationId?.let {
            log.debug("InstallationId: $installationId")
        } ?: run {
            val uuid = UUID.randomUUID().toString()
            preferences.edit().putString(
                PREFERENCES_AUTH_INSTALLATION_ID, uuid
            ).apply()
            log.debug("initialized InstallationId: $uuid")
        }
    }

    private fun isDataPolicyAccepted(): Boolean {
        val tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)
        return tazApiCssPreferences.contains(SETTINGS_DATA_POLICY_ACCEPTED)
    }

    private fun isFirstTimeStart(): Boolean {
        val tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)
        return !tazApiCssPreferences.contains(SETTINGS_FIRST_TIME_APP_STARTS)
    }

    private fun setupSentry() {
        log.info("setting up sentry")
        val preferences =
            applicationContext.getSharedPreferences(PREFERENCES_AUTH, Context.MODE_PRIVATE)
        val installationId = preferences.getString(PREFERENCES_AUTH_INSTALLATION_ID, null)

        val user = User()
        user.id = installationId

        Sentry.setUser(user)
    }


    /**
     * download AppInfo and persist it
     */
    private suspend fun ensureAppInfo() {
        dataService.getAppInfo(retryOnFailure = true)
    }

    /**
     * download AppInfo and persist it
     */
    private suspend fun checkAppVersion() {
        try {
            val appInfo = dataService.getAppInfo(allowCache = false)
            if (BuildConfig.DEBUG && appInfo.androidVersion > BuildConfig.VERSION_CODE) {
                NotificationHelper.getInstance(applicationContext).showNotification(
                    R.string.notification_new_version_title,
                    R.string.notification_new_version_body,
                    CHANNEL_ID_NEW_VERSION,
                    pendingIntent = PendingIntent.getActivity(
                        applicationContext,
                        NEW_VERSION_REQUEST_CODE,
                        downloadFromServerIntent(),
                        FLAG_CANCEL_CURRENT
                    )
                )
            }
        } catch (e: Exception) {
            log.warn("Start up check for new version failed because no internet available")
        }
    }

    private fun downloadFromServerIntent(): Intent {
        return Intent(Intent(Intent.ACTION_VIEW)).setData(Uri.parse(DEBUG_VERSION_DOWNLOAD_ENDPOINT))
    }

    /**
     * download resources, save to db and download necessary files
     */
    private fun initResources() {
        log.info("initializing resources")
        val fileHelper = FileHelper.getInstance(applicationContext)

        fileHelper.getFileByPath(RESOURCE_FOLDER).mkdirs()
        val tazApiCssFile = fileHelper.getFileByPath("$RESOURCE_FOLDER/tazApi.css")
        if (!tazApiCssFile.exists()) {
            tazApiCssFile.createNewFile()
            log.debug("Created tazApi.css")
        }

        val tazApiAssetPath = "js/tazApi.js"
        val tazApiJsFile = fileHelper.getFileByPath("$RESOURCE_FOLDER/tazApi.js")
        if (!tazApiJsFile.exists() || !fileHelper.assetFileSameContentAsFile(
                tazApiAssetPath,
                tazApiJsFile
            )
        ) {
            fileHelper.copyAssetFileToFile(tazApiAssetPath, tazApiJsFile)
            log.debug("Created/updated tazApi.js")
        }

    }

    private suspend fun sendPushToken() = withContext(Dispatchers.IO) {
        try {
            if (!firebaseHelper.hasTokenBeenSent && !firebaseHelper.firebaseToken.isNullOrEmpty()) {
                firebaseHelper.hasTokenBeenSent =
                    dataService.sendNotificationInfo(firebaseHelper.firebaseToken!!)
            }
        } catch (e: ConnectivityException.NoInternetException) {
            log.warn("Sending notification token failed because no internet available")
        }
    }

    private fun generateNotificationChannels() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            generateNotificationChannel(
                R.string.notification_channel_new_version,
                R.string.notification_channel_new_version_description,
                CHANNEL_ID_NEW_VERSION,
                NotificationManager.IMPORTANCE_HIGH
            )
            generateNotificationChannel(
                R.string.notification_channel_fcm,
                R.string.notification_channel_fcm_description,
                R.string.notification_fcm_channel_id,
                NotificationManager.IMPORTANCE_HIGH
            )
        }

    }

    @TargetApi(26)
    private fun generateNotificationChannel(
        @StringRes channelName: Int,
        @StringRes channeldDescription: Int,
        @StringRes channelId: Int,
        importance: Int
    ) {
        generateNotificationChannel(
            channelName,
            channeldDescription,
            getString(channelId),
            importance
        )
    }

    @TargetApi(26)
    private fun generateNotificationChannel(
        @StringRes channelName: Int,
        @StringRes channelDescription: Int,
        channelId: String,
        importance: Int
    ) {
        val name = getString(channelName)
        val descriptionText = getString(channelDescription)
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

class InitializationException(message: String, override val cause: Throwable? = null): Exception(message, cause)
