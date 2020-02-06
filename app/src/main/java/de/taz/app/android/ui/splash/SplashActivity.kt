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
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.QueryService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.download.DownloadService
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.*
import io.sentry.Sentry
import io.sentry.event.UserBuilder
import kotlinx.coroutines.*
import java.util.*

const val CHANNEL_ID_NEW_VERSION = "NEW_VERSION"
const val NEW_VERSION_REQUEST_CODE = 0

class SplashActivity : AppCompatActivity() {

    private val log by Log

    override fun onResume() {
        super.onResume()

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

        startActivity(Intent(this, MainActivity::class.java))
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

    private fun setupSentry() {
        val preferences =
            applicationContext.getSharedPreferences(PREFERENCES_AUTH, Context.MODE_PRIVATE)
        val installationId = preferences.getString(PREFERENCES_AUTH_INSTALLATION_ID, null)

        Sentry.getContext().user = UserBuilder()
            .setId(installationId)
            .build()
    }

    private fun createSingletons() {
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
            PreferencesHelper.createInstance(it)
            QueryService.createInstance(it)
            ToastHelper.createInstance(it)

            ApiService.createInstance(it)
            DownloadedIssueHelper.createInstance(it)

            FirebaseHelper.createInstance(it)
            NotificationHelper.createInstance(it)
        }
        log.debug("Singletons initialized")
    }

    private fun initFeedInformation() {
        val apiService = ApiService.getInstance(applicationContext)
        val feedRepository = FeedRepository.getInstance(applicationContext)
        val toastHelper = ToastHelper.getInstance(applicationContext)

        runBlocking(Dispatchers.IO) {
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
        val apiService = ApiService.getInstance(applicationContext)
        val issueRepository = IssueRepository.getInstance(applicationContext)
        val toastHelper = ToastHelper.getInstance(applicationContext)

        runBlocking(Dispatchers.IO) {
            try {
                val issues = apiService.getLastIssues()
                issueRepository.saveIfDoNotExist(issues)
                log.debug("Initialized Issues")
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                toastHelper.showNoConnectionToast()
                log.debug("Initializing Issues failed")
            }
        }
    }

    /**
     * download AppInfo and persist it
     */
    private fun initAppInfoAndCheckAndroidVersion() {
        runBlocking(Dispatchers.IO) {
            try {
                ApiService.getInstance(applicationContext).getAppInfo()?.let {
                    AppInfoRepository.getInstance(applicationContext).save(it)
                    log.warn("Initialized AppInfo")
                    if (it.androidVersion > BuildConfig.VERSION_CODE) {
                        NotificationHelper.getInstance().showNotification(
                            R.string.notification_new_version_title,
                            R.string.notification_new_version_body,
                            CHANNEL_ID_NEW_VERSION,
                            pendingIntent = PendingIntent.getActivity(
                                applicationContext,
                                NEW_VERSION_REQUEST_CODE,
                                if (BuildConfig.DEBUG) downloadFromServerIntent() else showInStoreIntent(),
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

    private fun showInStoreIntent(): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${applicationContext.packageName}")
        )
    }

    private fun downloadFromServerIntent(): Intent {
        return Intent(Intent(Intent.ACTION_VIEW)).setData(Uri.parse(DEBUG_VERSION_DOWNLOAD_ENDPOINT))
    }

    /**
     * download resources, save to db and download necessary files
     */
    private fun initResources() {
        val apiService = ApiService.getInstance(applicationContext)
        val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        val fileHelper = FileHelper.getInstance(applicationContext)
        val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fromServer = apiService.getResourceInfo()
                val local = resourceInfoRepository.get()

                fromServer?.let {
                    if (local == null || fromServer.resourceVersion > local.resourceVersion || !local.isDownloadedOrDownloading()) {
                        resourceInfoRepository.save(fromServer)

                        fromServer.resourceList.forEach { newFileEntry ->
                            fileEntryRepository.get(newFileEntry.name)?.let { oldFileEntry ->
                                // only delete modified files
                                if (oldFileEntry != newFileEntry) {
                                    oldFileEntry.deleteFile()
                                }
                            }
                        }

                        local?.let { resourceInfoRepository.delete(local) }

                        // ensure resources are downloaded
                        DownloadService.scheduleDownload(applicationContext, fromServer)
                        DownloadService.download(applicationContext, fromServer)
                        local?.let { log.debug("Initialized ResourceInfo") }
                            ?: log.debug("Updated ResourceInfo")
                    }
                }
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                log.warn("Initializing ResourceInfo failed")
            }
        }

        fileHelper.getFileByPath(RESOURCE_FOLDER).mkdirs()
        val tazApiCssFile = fileHelper.getFileByPath("$RESOURCE_FOLDER/tazApi.css")
        if (!tazApiCssFile.exists()) {
            tazApiCssFile.createNewFile()
            log.debug("Created tazApi.css")
        }
        val tazApiJsFile = fileHelper.getFileByPath("$RESOURCE_FOLDER/tazApi.js")
        if (!tazApiJsFile.exists()) {
            tazApiJsFile.writeText(fileHelper.readFileFromAssets("js/tazApi.js"))
            log.debug("Created tazApi.js")
        }
    }

    private fun deletePublicIssuesIfLoggedIn() {
        if (AuthHelper.getInstance().authStatusLiveData.value == AuthStatus.valid) {
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

    private fun generateNotificationChannels() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            generateNotificationChannel(
                R.string.channel_new_version,
                R.string.channel_new_version_description,
                CHANNEL_ID_NEW_VERSION,
                NotificationManager.IMPORTANCE_HIGH
            )
        }

    }

    @TargetApi(26)
    private fun generateNotificationChannel(
        @StringRes channelName: Int,
        @StringRes channeldDescription: Int,
        channelId: String,
        importance: Int
    ) {
        val name = getString(channelName)
        val descriptionText = getString(channeldDescription)
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

}

