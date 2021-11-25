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
import androidx.annotation.StringRes
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.*
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.*
import de.taz.app.android.base.StartupActivity
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.data.DataService
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.Log
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.StorageOrganizationActivity
import de.taz.app.android.util.NightModeHelper
import de.taz.app.android.util.showConnectionErrorDialog
import io.sentry.Sentry
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.Exception

const val CHANNEL_ID_NEW_VERSION = "NEW_VERSION"
const val NEW_VERSION_REQUEST_CODE = 0

class SplashActivity : StartupActivity() {

    private val log by Log

    private lateinit var dataService: DataService
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var authHelper: AuthHelper
    private lateinit var toastHelper: ToastHelper
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService
    private lateinit var storageDataStore: StorageDataStore
    private lateinit var contentService: ContentService
    private lateinit var issueRepository: IssueRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val splashScreen = installSplashScreen()
        splashScreen.setKeepVisibleCondition { true }

        dataService = DataService.getInstance(applicationContext)
        firebaseHelper = FirebaseHelper.getInstance(applicationContext)
        authHelper = AuthHelper.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        storageService = StorageService.getInstance(applicationContext)
        contentService = ContentService.getInstance(applicationContext)
        storageDataStore = StorageDataStore.getInstance(applicationContext)
        issueRepository = IssueRepository.getInstance(applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            launch { checkAppVersion() }
            launch { sendPushToken() }
        }
        lifecycleScope.launch {
            generateNotificationChannels()
            verifyStorageLocation()

            try {
                withContext(Dispatchers.IO) {
                    ensureAppInfo()
                    initResources()
                    initFeed()
                    launch {
                        checkForNewestIssue()
                    }
                    NightModeHelper.generateCssOverride(this@SplashActivity)
                }
            } catch (e: InitializationException) {
                log.error("Error while initializing")
                e.printStackTrace()
                showConnectionErrorDialog()
                Sentry.captureException(e)
                return@launch
            }

            val currentStorageLocation = storageDataStore.storageLocation.get()

            val unmigratedFiles = withContext(Dispatchers.IO) {
                fileEntryRepository.getDownloadedExceptStorageLocation(currentStorageLocation)
            }
            val filesWithBadStorage = withContext(Dispatchers.IO) {
                fileEntryRepository.getExceptStorageLocation(
                    listOf(StorageLocation.NOT_STORED, currentStorageLocation)
                )
            }


            val publicIssuesNeedDeletion =
                withContext(Dispatchers.IO) {
                    (issueRepository.getAllPublicAndDemoIssueStubs().count() > 0
                            && authHelper.getMinStatus() == IssueStatus.regular)
                }
            // Explicitly selectable storage migration, if there is any file to migrate start migration activity
            if (unmigratedFiles.isNotEmpty() || filesWithBadStorage.isNotEmpty() || publicIssuesNeedDeletion) {
                Intent(this@SplashActivity, StorageOrganizationActivity::class.java).apply {
                    startActivity(this)
                }
            } else {
                startActualApp()
            }
            finish()
        }
    }


    private suspend fun verifyStorageLocation() {
        // if the configured storage is set to external, but no external device is mounted we need to reset it
        // likely this happened because the user ejected the sd card
        if (storageDataStore.storageLocation.get() == StorageLocation.EXTERNAL && storageService.getExternalFilesDir() == null) {
            storageDataStore.storageLocation.set(StorageLocation.INTERNAL)
        }
    }

    private suspend fun initFeed() {
        try {
            val feed = dataService.getFeedByName(DISPLAYED_FEED)
            if (feed?.publicationDates?.isEmpty() == true) {
                dataService.getFeedByName(
                    DISPLAYABLE_NAME,
                    allowCache = false,
                    retryOnFailure = true
                )
            }
        } catch (e: ConnectivityException) {
            throw InitializationException("Could not retrieve feed during first start")
        }
    }

    private suspend fun checkForNewestIssue() {
        try {
            dataService.refreshFeedAndGetIssueKeyIfNew(DISPLAYED_FEED)
        } catch (e: ConnectivityException) {
            toastHelper.showConnectionToServerFailedToast()
        }
    }

    /**
     * download AppInfo and persist it
     */
    private suspend fun ensureAppInfo() {
        try {
            // This call might be duplicated by the AppVersion check which is not allowing cache.
            // To make this call not fail due to a connectivity exception if there indeed is a
            // cached AppInfo we need to force execute it and not listen on the same call as in checkAppVersion
            contentService.downloadMetadata(AppInfoKey(), forceExecution = true)
        } catch (exception: CacheOperationFailedException) {
            throw InitializationException("Retrieving AppInfo failed: $exception")
        }
    }

    /**
     * download AppInfo and persist it
     */
    private suspend fun checkAppVersion() {
        try {
            val appInfo = contentService.downloadMetadata(AppInfoKey(), allowCache = false) as AppInfo
            if (BuildConfig.MANUAL_UPDATE && appInfo.androidVersion > BuildConfig.VERSION_CODE) {
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
        return Intent(Intent(Intent.ACTION_VIEW)).setData(
            Uri.parse(
                DEBUG_VERSION_DOWNLOAD_ENDPOINT
            )
        )
    }

    /**
     * download resources, save to db and download necessary files
     */
    private suspend fun initResources() {
        log.info("initializing resources")

        val existingTazApiJSFileEntry = fileEntryRepository.get("tazApi.js")
        val existingTazApiCSSFileEntry = fileEntryRepository.get("tazApi.css")

        val currentStorageLocation = storageDataStore.storageLocation.get()
        storageService.getAbsolutePath(
            RESOURCE_FOLDER,
            storageLocation = currentStorageLocation
        )
            ?.let(::File)?.mkdirs()

        var tazApiJSFileEntry =
            if (existingTazApiJSFileEntry != null && existingTazApiJSFileEntry.storageLocation != StorageLocation.NOT_STORED) {
                existingTazApiJSFileEntry
            } else {
                val newFileEntry = FileEntry(
                    name = "tazApi.js",
                    storageType = StorageType.resource,
                    moTime = Date().time,
                    sha256 = "",
                    size = 0,
                    folder = RESOURCE_FOLDER,
                    dateDownload = null,
                    path = "$RESOURCE_FOLDER/tazApi.js",
                    storageLocation = currentStorageLocation
                )
                val newFile = storageService.getFile(newFileEntry)
                if (newFile?.exists() == true) {
                    fileEntryRepository.saveOrReplace(
                        newFileEntry.copy(
                            dateDownload = Date(),
                            size = newFile.length(),
                            sha256 = storageService.getSHA256(newFile)
                        )
                    )
                } else {
                    fileEntryRepository.saveOrReplace(newFileEntry)
                }
            }
        val tazApiAssetPath = "js/tazApi.js"
        val tazApiJSFile = try {
            storageService.getFile(tazApiJSFileEntry)
        } catch (e: ExternalStorageNotAvailableException) {
            // If card was ejected create new file
            tazApiJSFileEntry =
                fileEntryRepository.saveOrReplace(tazApiJSFileEntry.copy(storageLocation = StorageLocation.INTERNAL))
            storageService.getFile(tazApiJSFileEntry)
        }
        if (tazApiJSFile?.exists() == false || (tazApiJSFile != null && !storageService.assetFileSameContentAsFile(
                tazApiAssetPath,
                tazApiJSFile
            ))
        ) {
            storageService.copyAssetFileToFile(tazApiAssetPath, tazApiJSFile)
            fileEntryRepository.saveOrReplace(
                tazApiJSFileEntry.copy(
                    dateDownload = Date(),
                    size = tazApiJSFile.length(),
                    sha256 = storageService.getSHA256(tazApiJSFile)
                )
            )
            log.debug("Created/updated tazApi.js")
        }

        var tazApiCSSFileEntry =
            if (existingTazApiCSSFileEntry != null && existingTazApiCSSFileEntry.storageLocation != StorageLocation.NOT_STORED) {
                existingTazApiCSSFileEntry
            } else {
                val newFileEntry = FileEntry(
                    name = "tazApi.css",
                    storageType = StorageType.resource,
                    moTime = Date().time,
                    sha256 = "",
                    size = 0,
                    folder = RESOURCE_FOLDER,
                    dateDownload = null,
                    path = "$RESOURCE_FOLDER/tazApi.css",
                    storageLocation = currentStorageLocation
                )
                val newFile = storageService.getFile(newFileEntry)
                if (newFile?.exists() == true) {
                    fileEntryRepository.saveOrReplace(
                        newFileEntry.copy(
                            dateDownload = Date(),
                            size = newFile.length(),
                            sha256 = storageService.getSHA256(newFile)
                        )
                    )
                } else {
                    fileEntryRepository.saveOrReplace(newFileEntry)
                }
            }

        val tazApiCSSFile = try {
            storageService.getFile(tazApiCSSFileEntry)
        } catch (e: ExternalStorageNotAvailableException) {
            // If card was ejected create new file
            tazApiCSSFileEntry =
                fileEntryRepository.saveOrReplace(tazApiCSSFileEntry.copy(storageLocation = StorageLocation.INTERNAL))
            storageService.getFile(tazApiCSSFileEntry)
        }
        if (tazApiCSSFile?.exists() == false) {
            tazApiCSSFile.createNewFile()
            fileEntryRepository.saveOrReplace(
                tazApiCSSFileEntry.copy(
                    dateDownload = Date(),
                    size = tazApiCSSFile.length(),
                    sha256 = storageService.getSHA256(tazApiCSSFile),
                )
            )
            log.debug("Created tazApi.css")
        }
        try {
            contentService.downloadToCache(
                ResourceInfoKey(-1)
            )
        } catch (e: CacheOperationFailedException) {
            val hint = "Error while trying to download resource info on startup"
            log.warn(hint)
            Sentry.captureException(e, hint)
        }
    }

    private suspend fun sendPushToken() = withContext(Dispatchers.IO) {
        try {
            val token = firebaseHelper.token.get()
            if (!firebaseHelper.tokenSent.get() && !token.isNullOrEmpty()) {
                firebaseHelper.tokenSent.set(
                    dataService.sendNotificationInfo(token)
                )
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

class InitializationException(message: String, override val cause: Throwable? = null) :
    Exception(message, cause)
