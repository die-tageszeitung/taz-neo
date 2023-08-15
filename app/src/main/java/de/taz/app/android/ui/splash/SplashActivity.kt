package de.taz.app.android.ui.splash

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException
import de.taz.app.android.*
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.*
import de.taz.app.android.base.StartupActivity
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.FeedService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.StorageOrganizationActivity
import de.taz.app.android.util.Log
import de.taz.app.android.util.clearCustomPDFThumbnailLoaderCache
import de.taz.app.android.util.showConnectionErrorDialog
import io.sentry.Sentry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.*

const val CHANNEL_ID_NEW_VERSION = "NEW_VERSION"
const val NEW_VERSION_REQUEST_CODE = 0
private const val MAX_RETRIES_ON_STARTUP = 3
private const val DOWNLOAD_TASKS_TIMEOUT_MS = 2_000L
private const val MIN_VERSION_QUERY_TIMEOUT_MS = 250L

class SplashActivity : StartupActivity() {

    private val log by Log

    private lateinit var authHelper: AuthHelper
    private lateinit var apiService: ApiService
    private lateinit var toastHelper: ToastHelper
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService
    private lateinit var storageDataStore: StorageDataStore
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var contentService: ContentService
    private lateinit var issueRepository: IssueRepository
    private lateinit var feedService: FeedService
    private lateinit var appInfoRepository: AppInfoRepository
    private lateinit var feedRepository: FeedRepository
    private lateinit var resourceInfoRepository: ResourceInfoRepository

    private var showSplashScreen = true

    private var splashStartMs = 0L
    private var initComplete = false

    private var minVersionDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        splashStartMs = System.currentTimeMillis()
        installSplashScreen().apply {
            setKeepOnScreenCondition { showSplashScreen }
        }
        super.onCreate(savedInstanceState)

        authHelper = AuthHelper.getInstance(applicationContext)
        apiService = ApiService.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        storageService = StorageService.getInstance(applicationContext)
        contentService = ContentService.getInstance(applicationContext)
        storageDataStore = StorageDataStore.getInstance(applicationContext)
        generalDataStore = GeneralDataStore.getInstance(applicationContext)
        issueRepository = IssueRepository.getInstance(applicationContext)
        feedService = FeedService.getInstance(applicationContext)
        appInfoRepository = AppInfoRepository.getInstance(applicationContext)
        feedRepository = FeedRepository.getInstance(applicationContext)
        resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)

        lifecycleScope.launch {
            initialize()
        }
    }

    override fun onStop() {
        super.onStop()
        // Log if the user closed the SplashActivity Screen before it was initialized completely.
        // There is a good chance that this was due to her waiting for the activity for too long,
        // probably because of slow internet.
        // As Android sometimes seem to start the activity without doing any work in the initialize
        // coroutine we are only tracking times that take longer then 1s to sentry.
        val splashTimeMs = System.currentTimeMillis() - splashStartMs
        if (!initComplete && splashTimeMs > 1_000) {
            log.warn("SplashActivity stopped after ${splashTimeMs}ms")
            Sentry.captureMessage("SplashActivity was closed before the initialization was complete")
        }
    }

    override fun onDestroy() {
        // Ensure that the SplashScreen.setKeepOnScreenCondition is set to false, to prevent a bug
        // on LineageOS occurring on a cold App start. Somehow the SplashActivity will be started
        // twice. Although the first instance is finished immediately it does remain on screen
        // blocking the the second instance that would be showing the ConnectionErrorDialog as long
        // as the setKeepOnScreenCondition is true.
        showSplashScreen = false
        minVersionDialog?.dismiss()
        minVersionDialog = null
        super.onDestroy()
    }

    override fun onAttachedToWindow() {
        // Since Android sdk 28 there is a notch/cut-out support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val displayCutout = window.decorView.rootWindowInsets.displayCutout
            val cutoutHeight = displayCutout?.safeInsetTop
            cutoutHeight?.let {
                if (it > 0) {
                    val extraPadding =
                        it + resources.getDimensionPixelSize(R.dimen.space_between_status_bar_and_content_applied_when_cutout) - resources.getDimensionPixelSize(
                            R.dimen.drawer_logo_translation_y
                        )
                    applicationScope.launch {
                        generalDataStore.displayCutoutExtraPadding.set(extraPadding)
                    }
                }
            }
        }
        super.onAttachedToWindow()
    }

    private suspend fun initialize() {
        log.verbose("Start initialize")

        // Run the general initialization logic that is required to start the App.
        // These steps must run before the download tasks may be started
        generateNotificationChannels()
        verifyStorageLocation()
        initResources()

        if (!checkMinVersion()) {
            // Stop the initialization if the min version is not met.
            // As we did not start the downloads and thus won't end up in a broken state
            // we can mark the initComplete
            initComplete = true
            return
        }

        // Create async coroutines for all init tasks that might require network for downloads.
        // The [Deferred]s can be awaited with a timeout if the network is very slow.
        // Starting them async has the advantage of possible parallelization.
        // And it is required to be able to use a timeout, as for some reason CacheOperation.execute
        // is using a NonCancellable CoroutineContext.
        // Note that the async Coroutines will continue even when this Activity is closed due to
        // them being NonCancellable
        val downloadTasks = listOf(
            lifecycleScope.async {
                // Note that both of these are using [ContentService] with the same tag and
                // will be serialized internally by the download manager:
                // thus there is no advantage in starting them on separate coroutines
                checkAppVersion()
                ensureAppInfo()
            },
            lifecycleScope.async {
                downloadResourceFiles()
            },
            lifecycleScope.async {
                initFeed()
            }
        )

        // First we'll try to await the download tasks with a timeout, if we hit the timeout we will
        // check if all the required offline data is ready and start the main app.
        try {
            withTimeout(DOWNLOAD_TASKS_TIMEOUT_MS) {
                downloadTasks.awaitAll()
            }

        } catch (e: InitializationException) {
            handleInitializationException(e)
            return

        } catch (e: TimeoutCancellationException) {
            log.debug("Initialization download tasks took longer than ${DOWNLOAD_TASKS_TIMEOUT_MS}ms")
            if (isOfflineReady()) {
                log.debug("Offline data is ready - skip waiting for downloads and start App immediately")
                finishOnInitCompleteAndContinue()
                return
            }
        }

        // Otherwise, if the offline data is not ready, we continue waiting for the download tasks.
        // If they have already finished, this second await call will return immediately.
        try {
            downloadTasks.awaitAll()
        } catch (e: InitializationException) {
            handleInitializationException(e)
            return
        }
        finishOnInitCompleteAndContinue()
    }

    private suspend fun verifyStorageLocation() {
        // if the configured storage is set to external, but no external device is mounted we need to reset it
        // likely this happened because the user ejected the sd card
        if (storageDataStore.storageLocation.get() == StorageLocation.EXTERNAL && storageService.getExternalFilesDir() == null) {
            log.debug("StorageLocation moved to internal")
            storageDataStore.storageLocation.set(StorageLocation.INTERNAL)
        }
    }

    private suspend fun initFeed() {
        try {
            log.verbose("Start initializing feed")
            // First try to get the latest feed. This will refresh the feed automatically if it has
            // not been fetched yet.
            val feed = feedService.getFeedFlowByName(BuildConfig.DISPLAYED_FEED).first()

            // If for any reason we don't have a feed with publication dates, we will try once more
            // to refresh the feed from the API - in case that for unknown reasons only the the
            // locally cached feed is missing the publication dates.
            // Note: we assume that any feed that is refreshed from the API will contain non null,
            // non empty publication dates.
            val hasPublicationDate = feed?.publicationDates?.isNotEmpty() == true
            if (!hasPublicationDate) {
                feedService.refreshFeed(BuildConfig.DISPLAYED_FEED)
            }
            log.verbose("Finished initializing feed")

        } catch (e: ConnectivityException) {
            throw InitializationException("Could not retrieve feed during first start", e)
        }
    }

    /**
     * Try downloading the latest AppInfo and check if a newer App Version is available.
     * Any errors will be ignored.
     */
    private suspend fun checkAppVersion() {
        try {
            log.verbose("Start checking AppVersion")

            val appInfo = contentService.downloadMetadata(
                AppInfoKey(),
                allowCache = false,
                maxRetries = MAX_RETRIES_ON_STARTUP
            ) as AppInfo
            log.debug("AppInfo was downloaded and persisted in checkAppVersion()")

            if (BuildConfig.MANUAL_UPDATE && appInfo.androidVersion > BuildConfig.VERSION_CODE) {
                NotificationHelper.getInstance(applicationContext).showNotification(
                    R.string.notification_new_version_title,
                    R.string.notification_new_version_body,
                    CHANNEL_ID_NEW_VERSION,
                    pendingIntent = PendingIntent.getActivity(
                        applicationContext,
                        NEW_VERSION_REQUEST_CODE,
                        downloadFromServerIntent(),
                        FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        } catch (e: Exception) {
            log.warn("Start up check for new version failed", e)
        }
    }

    /**
     * Ensure that a AppInfo does exist in cache.
     * If there is none cached yet, it is tried to download it.
     * Errors will be catched and rethrown as [InitializationException]
     */
    private suspend fun ensureAppInfo() {
        try {
            log.verbose("Start ensureAppInfo()")
            // This call might be duplicated by the AppVersion check which is not allowing cache.
            // To make this call not fail due to a connectivity exception if there indeed is a
            // cached AppInfo we need to force execute it and not listen on the same call as in checkAppVersion
            contentService.downloadMetadata(
                AppInfoKey(),
                forceExecution = true,
                maxRetries = MAX_RETRIES_ON_STARTUP
            )
            log.verbose("AppInfo is available in cache (maybe downloaded) in ensureAppInfo()")

        } catch (exception: CacheOperationFailedException) {
            throw InitializationException("Retrieving AppInfo failed", exception)
        }
    }


    /**
     * download resources, save to db and download necessary files
     */
    private suspend fun downloadResourceFiles() {
        try {
            log.verbose("Start downloading ResourceInfo")
            contentService.downloadToCache(
                ResourceInfoKey(-1)
            )
            log.verbose("Finished downloading ResourceInfo")
        } catch (e: CacheOperationFailedException) {
            throw InitializationException("ResourceInfo download failed on startup", e)
        }
    }



    private fun downloadFromServerIntent(): Intent {
        return Intent(Intent(Intent.ACTION_VIEW)).setData(
            Uri.parse(
                DEBUG_VERSION_DOWNLOAD_ENDPOINT
            )
        )
    }

    private suspend fun initResources() {
        log.verbose("Initialize resource files on the file storage")
        ResourceInitUtil(applicationContext).apply {
            ensureDefaultNavButtonExists()
            ensureTazApiJsExists()
            ensureTazApiCssExists()
        }
        log.verbose("Finished initializing resources")
    }

    private fun generateNotificationChannels() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (BuildConfig.MANUAL_UPDATE) {
                generateNotificationChannel(
                    R.string.notification_channel_fcm_new_version_title,
                    R.string.notification_channel_fcm_new_version_description,
                    R.string.notification_channel_fcm_new_version_id,
                    NotificationManager.IMPORTANCE_HIGH
                )
            }
            generateNotificationChannel(
                R.string.notification_channel_fcm_new_issue_arrived_title,
                R.string.notification_channel_fcm_new_issue_arrived_description,
                R.string.notification_channel_fcm_new_issue_arrived_id,
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

    /**
     * Handle initialization errors by showing a error dialog
     */
    private fun handleInitializationException(e: InitializationException) {
        log.error("Error while initializing")
        e.printStackTrace()
        // hide splash screen so dialog can be shown
        showSplashScreen = false
        showConnectionErrorDialog()
        Sentry.captureException(e)
    }

    /**
     * To be called if the initialization is complete and the the app ready to be started.
     * Will finish the SplashActivity, start background tasks and continue to the next Activity
     */
    private suspend fun finishOnInitCompleteAndContinue() {
        initComplete = true
        startBackgroundTasks()

        // Explicitly selectable storage migration, if there is any file to migrate start migration activity
        if (areMigrationsRequired()) {
            Intent(this@SplashActivity, StorageOrganizationActivity::class.java).apply {
                startActivity(this)
            }
        } else {
            startActualApp()
        }

        finish()
    }

    /**
     * Start background tasks on a Job launched in the background on the applicationScope, thus it
     * will not be canceled when the [SplashActivity] finishes.
     */
    private fun startBackgroundTasks() {
        applicationScope.launch {
            checkForNewestIssue(feedService, toastHelper)
            generalDataStore.clearRemovedEntries()
            clearCustomPDFThumbnailLoaderCache(applicationContext)
        }
    }


    /**
     * Returns true if some migrations are pending, due to leftover public artifacts
     * or when the app data should be moved from/to the SD card.
     */
    private suspend fun areMigrationsRequired(): Boolean {
        val currentStorageLocation = storageDataStore.storageLocation.get()

        val unmigratedFiles =
            fileEntryRepository.getDownloadedExceptStorageLocation(currentStorageLocation)
        val filesWithBadStorage =
            fileEntryRepository.getExceptStorageLocation(
                listOf(StorageLocation.NOT_STORED, currentStorageLocation)
            )


        val publicIssuesNeedDeletion =
            (issueRepository.getAllPublicAndDemoIssueStubs().isNotEmpty()
                    && authHelper.getMinStatus() == IssueStatus.regular)

        return unmigratedFiles.isNotEmpty() || filesWithBadStorage.isNotEmpty() || publicIssuesNeedDeletion
    }

    /**
     * Returns true if all the required data to start the App offline is ready.
     * Short circuits around any network delays that might be hit when using the [ContentService]
     */
    private suspend fun isOfflineReady(): Boolean {
        val latestAppInfo = appInfoRepository.get()
        val latestFeed = feedRepository.get(BuildConfig.DISPLAYED_FEED)
        val latestResourceInfo = resourceInfoRepository.getNewest()

        return latestAppInfo != null
                && latestFeed != null && latestFeed.publicationDates.isNotEmpty()
                && latestResourceInfo != null && latestResourceInfo.dateDownload != null
    }

    /**
     * Returns true if the minVersion is satisfied by the current installation.
     * If not it shows an error dialog and returns false.
     * The caller is responsible to prevent any further API calls if it returns false.
     * In case of network errors or if the [MIN_VERSION_QUERY_TIMEOUT_MS] is hit, the minVersion
     * check is skipped until the next app start.
     */
    private suspend fun checkMinVersion(): Boolean {
        try {
            val minVersion = withTimeout(MIN_VERSION_QUERY_TIMEOUT_MS) {
                apiService.getMinAppVersion()
            }

            if (minVersion == null) {
                log.error("Could not get minVersion from API. Skip check and continue.")
                return true
            }


            val currentVersion = getCurrentAppVersion()
            if (currentVersion == null) {
                return true
            }

            if (currentVersion.isLowerThan(minVersion)) {
                showMinVersionDialog(minVersion, currentVersion)
                return false
            }

        } catch (e : ConnectivityException) {
            log.error("Could not get the minVersion on startup. Skip check until next app start and continue.", e)
        } catch (e: TimeoutCancellationException) {
            log.warn("Could not get the minVersion on startup in time. Skip check until next app start and continue.")
        }
        return true
    }

    private fun getCurrentAppVersion(): Semver? {
        return try {
            Semver(BuildConfig.VERSION_NAME, Semver.SemverType.LOOSE)
        } catch (e: SemverException) {
            log.error("Could not get current app version from versionName: ${BuildConfig.VERSION_NAME}")
            null
        }
    }

    private fun showMinVersionDialog(minVersion: Semver, currentVersion: Semver) {
        // Stop showing the splash screen to ensure the dialog is shown
        showSplashScreen = false

        val message =
            getString(R.string.required_min_version_description, currentVersion, minVersion)

        minVersionDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.required_min_version_title)
            .setMessage(message)
            .setPositiveButton(R.string.close_okay) { _, _ ->
                openUpdateOption()
                finish()
            }
            .setNegativeButton(R.string.cancel_button) { _, _ -> finish() }
            .setCancelable(false)
            .setOnDismissListener { finish() }
            .show()
    }

    private fun openUpdateOption() {
        if (BuildConfig.IS_NON_FREE) {
            openMarket()
        } else {
            val uri = Uri.parse(getString(R.string.app_free_download_link))
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun openMarket() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
            )
        }
    }
}

class InitializationException(message: String, override val cause: Throwable? = null) :
    Exception(message, cause)


// This function will be called as part of a applicationScope bound coroutine that is not canceled when the SplashActivity finishes.
// Thus we can not throw the InitializationException to showConnectionErrorDialog() but have to fall back to a basic toast.
private suspend fun checkForNewestIssue(feedService: FeedService, toastHelper: ToastHelper) {
    try {
        feedService.refreshFeedAndGetIssueKeyIfNew(BuildConfig.DISPLAYED_FEED)
    } catch (e: ConnectivityException) {

        toastHelper.showConnectionToServerFailedToast()
    }
}
