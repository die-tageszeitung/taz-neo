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
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.BuildConfig
import de.taz.app.android.DEBUG_VERSION_DOWNLOAD_ENDPOINT
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.QueryService
import de.taz.app.android.api.models.*
import de.taz.app.android.download.DownloadService
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.util.Log
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.DataPolicyActivity
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.util.SubscriptionPollHelper
import io.sentry.Sentry
import io.sentry.event.UserBuilder
import kotlinx.coroutines.*
import java.lang.Exception
import java.util.*

const val CHANNEL_ID_NEW_VERSION = "NEW_VERSION"
const val NEW_VERSION_REQUEST_CODE = 0

class SplashActivity : AppCompatActivity() {

    private val log by Log

    override fun onResume() {
        super.onResume()
        log.info("splashactivity onresume called")

        setupSentry()

        generateInstallationId()
        generateNotificationChannels()

        createSingletons()

        initLastIssues()
        initFeedInformation()
        initAppInfoAndCheckAndroidVersion()
        initResources()

        deletePublicIssuesIfLoggedIn()

        ensurePushTokenSent()

        cleanUpImages()

        if (isDataPolicyAccepted()) {
            if (isFirstTimeStart()) {
                startActivity(Intent(this, WelcomeActivity::class.java))
            } else {
                startActivity(Intent(this, MainActivity::class.java))
            }
        } else {
            startActivity(Intent(this, DataPolicyActivity::class.java))
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

        Sentry.getContext().user = UserBuilder()
            .setId(installationId)
            .build()
    }

    private fun createSingletons() {
        log.info("creating singletons")
        applicationContext.let {
            AppDatabase.createInstance(it)

            AppInfoRepository.createInstance(it)
            ArticleRepository.createInstance(it)
            DownloadRepository.createInstance(it)
            FileEntryRepository.createInstance(it)
            IssueRepository.createInstance(it)
            PageRepository.createInstance(it)
            ResourceInfoRepository.createInstance(it)
            SectionRepository.createInstance(it)

            AuthHelper.createInstance(it)
            DateHelper.createInstance(it)
            FileHelper.createInstance(it)
            FeedHelper.createInstance(it)
            QueryService.createInstance(it)
            ToastHelper.createInstance(it)

            ApiService.createInstance(it)
            DownloadService.createInstance(it)
            DownloadedIssueHelper.createInstance(it)

            SubscriptionPollHelper.createInstance(it)

            FirebaseHelper.createInstance(it)
            NotificationHelper.createInstance(it)
        }
        log.debug("Singletons initialized")
    }

    private fun initFeedInformation() {
        val apiService = ApiService.getInstance(applicationContext)
        val feedRepository = FeedRepository.getInstance(applicationContext)
        val toastHelper = ToastHelper.getInstance(applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val feeds = apiService.getFeeds()
                feedRepository.save(feeds)
                log.debug("Initialized Feeds")
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                toastHelper.showNoConnectionToast()
                log.debug("Initializing Feeds failed")
            }
        }
    }

    private fun initLastIssues() {
        CoroutineScope(Dispatchers.IO).launch {
            initIssues(10)
        }
    }

    private suspend fun initIssues(number: Int) = withContext(Dispatchers.IO) {
        val apiService = ApiService.getInstance(applicationContext)
        val issueRepository = IssueRepository.getInstance(applicationContext)
        val toastHelper = ToastHelper.getInstance(applicationContext)

        try {
            val issues = apiService.getLastIssues(number)
            issueRepository.saveIfDoNotExist(issues)
            log.debug("Initialized Issues: ${issues.size}")
        } catch (e: ApiService.ApiServiceException.NoInternetException) {
            toastHelper.showNoConnectionToast()
            log.warn("Initializing Issues failed")
        }
    }


    /**
     * download AppInfo and persist it
     */
    private fun initAppInfoAndCheckAndroidVersion() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppInfo.update()?.let {
                    if (BuildConfig.DEBUG && it.androidVersion > BuildConfig.VERSION_CODE) {
                        NotificationHelper.getInstance().showNotification(
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
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                log.warn("Initializing AppInfo failed")
            }
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

        CoroutineScope(Dispatchers.IO).launch {
            ResourceInfo.update()
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
    }

    private fun deletePublicIssuesIfLoggedIn() {
        if (AuthHelper.getInstance().isLoggedIn()) {
            log.debug("Deleting public Issues")
            IssueRepository.getInstance().deletePublicIssues()
        }
    }

    private fun ensurePushTokenSent() {
        val firebaseHelper = FirebaseHelper.getInstance()
        if (!firebaseHelper.hasTokenBeenSent) {
            if (!firebaseHelper.firebaseToken.isNullOrEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    ApiService.getInstance().sendNotificationInfo()
                }
            }
        }
    }

    /**
     * This fixes an error introduced in 0.6.5 - TODO remove in 0.7 or so
     */
    private fun cleanUpImages() = runBlocking(Dispatchers.IO) {
        val imageFileEntryNames =
            FileEntryRepository.getInstance(applicationContext)
                .getFileNamesContaining("%Media%").toMutableList()
        imageFileEntryNames.addAll(
            FileEntryRepository.getInstance(applicationContext)
                .getFileNamesContaining("%Moment%")
        )

        val imageRepository = ImageRepository.getInstance(applicationContext)
        val downloadRepository = DownloadRepository.getInstance(applicationContext)

        imageFileEntryNames.forEach {
            if (imageRepository.get(it) == null) {
                try {
                    downloadRepository.setStatus(it, DownloadStatus.pending)
                } catch (e: Exception) {
                    // download does not exist
                }
                if (it.contains(".norm") || it.contains(".quadrat")) {
                    imageRepository.saveStub(
                        ImageStub(
                            it,
                            ImageType.picture,
                            1f,
                            ImageResolution.normal
                        )
                    )
                } else if (it.contains(".high")) {
                    imageRepository.saveStub(
                        ImageStub(
                            it,
                            ImageType.picture,
                            1f,
                            ImageResolution.high
                        )
                    )
                    if (it.contains("Moment")) {
                        MomentRepository.getInstance(applicationContext).getByImageName(it)
                            ?.download()
                    }
                } else {
                    imageRepository.saveStub(
                        ImageStub(
                            it,
                            ImageType.picture,
                            1f,
                            ImageResolution.small
                        )
                    )
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

}

