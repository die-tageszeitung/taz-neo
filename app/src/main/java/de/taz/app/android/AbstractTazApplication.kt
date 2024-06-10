package de.taz.app.android

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.StrictMode
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.scrubber.enqueueScrubberWorker
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.IssueCountHelper
import de.taz.app.android.singletons.NightModeHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.Log
import de.taz.app.android.util.UncaughtExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

abstract class AbstractTazApplication : Application() {
    private val log by Log

    private lateinit var authHelper: AuthHelper
    private lateinit var generalDataStore: GeneralDataStore
    private var _tracker: Tracker? = null

    // use this scope if you want to run code which should not terminate if the lifecycle of
    // a fragment or activity is finished
    val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // Install the global exception handler
        UncaughtExceptionHandler(applicationContext)

        authHelper = AuthHelper.getInstance(applicationContext)
        generalDataStore = GeneralDataStore.getInstance(applicationContext)

        applicationScope.launch {
            generateInstallationId()
            setUpSentry()
        }

        if (BuildConfig.DEBUG) {
            val vmPolicyBuilder = StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .penaltyLog()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                vmPolicyBuilder.detectNonSdkApiUsage()
            }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vmPolicyBuilder.detectUnsafeIntentLaunch()
            }

            StrictMode.setVmPolicy(vmPolicyBuilder.build())
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .penaltyLog()
                    .build()
            )
        }

        setupTracker()
        enqueueScrubberWorker(this)

        FirebaseHelper.getInstance(this)
        NightModeHelper.getInstance(this)
        IssueCountHelper.getInstance(this)
    }

    override fun onLowMemory() {
        _tracker?.dispatch()
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        when (level) {
            TRIM_MEMORY_UI_HIDDEN, TRIM_MEMORY_COMPLETE -> _tracker?.dispatch()
        }
        super.onTrimMemory(level)
    }

    private suspend fun generateInstallationId() {
        val installationId = authHelper.installationId.get()
        if (installationId.isEmpty()) {
            val uuid = UUID.randomUUID().toString()
            authHelper.installationId.set(uuid)
            log.debug("initialized InstallationId: $uuid")
        } else {
            log.debug("InstallationId: $installationId")
        }
    }

    private suspend fun setUpSentry() = withContext(Dispatchers.IO) {
        SentryWrapper.init(this@AbstractTazApplication)
        val userId = authHelper.installationId.get()
        SentryWrapper.setUser(userId)
    }


    // region tracking
    private fun setupTracker() {
        val tracker = Tracker.getInstance(applicationContext)

        _tracker = tracker
        ProcessLifecycleOwner.get().lifecycle.addObserver(trackerLifecycleObserver)

        applicationScope.launch {
            // Keep listening to changes and en/disable the tracker accordingly
            generalDataStore.consentToTracking.asFlow()
                .distinctUntilChanged()
                .collect { isEnabled ->
                    if (isEnabled) {
                        tracker.enable()

                        // Track this download (only tracks once per version)
                        tracker.trackDownload(BuildConfig.VERSION_NAME)
                    } else {
                        tracker.disable()
                    }
                }
        }
    }

    private val trackerLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            _tracker?.apply {
                trackAppIsBackgroundedEvent()
                dispatch()
            }
        }
    }
    // endregion
}


fun Activity.getTazApplication(): AbstractTazApplication = application as AbstractTazApplication
fun Fragment.getTazApplication(): AbstractTazApplication = requireActivity().getTazApplication()