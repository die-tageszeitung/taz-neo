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
import androidx.annotation.StringRes
import androidx.lifecycle.Observer
import de.taz.app.android.BuildConfig
import de.taz.app.android.DEBUG_VERSION_DOWNLOAD_ENDPOINT
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.*
import de.taz.app.android.base.BaseActivity
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.util.Log
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.DataPolicyActivity
import de.taz.app.android.ui.START_HOME_ACTIVITY
import de.taz.app.android.ui.WelcomeActivity
import io.sentry.Sentry
import io.sentry.event.UserBuilder
import kotlinx.coroutines.*
import java.util.*

const val CHANNEL_ID_NEW_VERSION = "NEW_VERSION"
const val NEW_VERSION_REQUEST_CODE = 0

class SplashActivity : BaseActivity() {

    private val log by Log

    override fun onResume() {
        super.onResume()
        log.info("splashactivity onresume called")

        setupSentry()

        generateInstallationId()
        generateNotificationChannels()

        initResources()
        initLastIssues()

        initAppInfoAndCheckAndroidVersion()
        initFeedInformation()

        deleteUnnecessaryIssues()

        ensurePushTokenSent()

        startNotDownloadedIssues()

        if (isDataPolicyAccepted()) {
            if (isFirstTimeStart()) {
                val intent = Intent(this, WelcomeActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                intent.putExtra(START_HOME_ACTIVITY, true)
                startActivity(intent)
            } else {
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                startActivity(intent)
            }
        } else {
            val intent = Intent(this, DataPolicyActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
        }
        finish()
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

        Sentry.getContext().user = UserBuilder()
            .setId(installationId)
            .build()
    }


    private fun initFeedInformation() {
        val apiService = ApiService.getInstance(applicationContext)
        val feedRepository = FeedRepository.getInstance(applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            val feeds = apiService.getFeedsAsync().await()
            feedRepository.save(feeds)
            log.debug("Initialized Feeds")
        }
    }

    private fun initLastIssues() {
        CoroutineScope(Dispatchers.IO).launch {
            val liveData = ResourceInfoRepository.getInstance(applicationContext).getLiveData()
            withContext(Dispatchers.Main) {
                liveData.observeForever(object : Observer<ResourceInfo?> {
                    override fun onChanged(t: ResourceInfo?) {
                        if (t?.downloadedStatus == DownloadStatus.done) {
                            log.error("ASDGA")
                            liveData.removeObserver(this)
                            CoroutineScope(Dispatchers.IO).launch { initIssues(10) }
                        }
                    }
                })
            }
        }
    }

    private suspend fun initIssues(number: Int) = withContext(Dispatchers.IO) {
        val apiService = ApiService.getInstance(applicationContext)
        val issueRepository = IssueRepository.getInstance(applicationContext)
        val toDownloadIssueHelper = ToDownloadIssueHelper.getInstance(applicationContext)

        val issues = apiService.getLastIssuesAsync(number).await()
        issueRepository.getLatestIssue()?.let {
            val newestDBIssueDate = issues.first().date
            if (it.date != newestDBIssueDate) {
                toDownloadIssueHelper.startMissingDownloads(
                    newestDBIssueDate,
                    it.date
                )
            }
        }

        issueRepository.saveIfDoNotExist(issues)
        log.debug("Initialized Issues: ${issues.size}")
        issues.forEach { it.moment.download(applicationContext) }
    }


    /**
     * download AppInfo and persist it
     */
    private fun initAppInfoAndCheckAndroidVersion() {
        CoroutineScope(Dispatchers.IO).launch {
            AppInfo.update(applicationContext)?.let {
                if (BuildConfig.DEBUG && it.androidVersion > BuildConfig.VERSION_CODE) {
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
            }
        }
    }

    private fun downloadFromServerIntent(): Intent {
        return Intent(Intent(Intent.ACTION_VIEW)).setData(Uri.parse(DEBUG_VERSION_DOWNLOAD_ENDPOINT))
    }

    /**
     * download resources, save to db and download necessary files
     */
    private fun initResources(): Job {
        log.info("initializing resources")
        val fileHelper = FileHelper.getInstance(applicationContext)

        val job = CoroutineScope(Dispatchers.IO).launch {
            ResourceInfo.update(applicationContext)
        }

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
        return job
    }

    private fun deleteUnnecessaryIssues() {
        val issueRepository = IssueRepository.getInstance(applicationContext)
        if (AuthHelper.getInstance(applicationContext).isLoggedIn()) {
            log.debug("Deleting public Issues")
            issueRepository.deletePublicIssues()
        } else {
            issueRepository.deleteNotDownloadedRegularIssues()
        }
    }

    private fun ensurePushTokenSent() {
        val firebaseHelper = FirebaseHelper.getInstance(applicationContext)
        if (!firebaseHelper.hasTokenBeenSent) {
            if (!firebaseHelper.firebaseToken.isNullOrEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    ApiService.getInstance(applicationContext).sendNotificationInfoAsync()
                }
            }
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

    private fun startNotDownloadedIssues() {
        CoroutineScope(Dispatchers.IO).launch {
            IssueRepository.getInstance(applicationContext).getDownloadStartedIssues().forEach {
                it.download(applicationContext)
            }
        }
    }
}

