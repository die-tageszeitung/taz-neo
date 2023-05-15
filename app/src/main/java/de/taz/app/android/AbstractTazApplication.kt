package de.taz.app.android

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.StrictMode
import androidx.fragment.app.Fragment
import com.facebook.stetho.Stetho
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.IssueCountHelper
import de.taz.app.android.singletons.NightModeHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.UncaughtExceptionHandler
import io.sentry.Sentry
import io.sentry.protocol.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

abstract class AbstractTazApplication : Application() {
    private val log by Log

    private lateinit var authHelper: AuthHelper

    // use this scope if you want to run code which should not terminate if the lifecycle of
    // a fragment or activity is finished
    val applicationScope = CoroutineScope(SupervisorJob())

    // Global flag used to ensure that the elapsed popup is only shown once across all Fragments/Activities
    var elapsedPopupAlreadyShown = false

    override fun onCreate() {
        super.onCreate()

        // Install the global exception handler
        UncaughtExceptionHandler(applicationContext)

        authHelper = AuthHelper.getInstance(this)
        applicationScope.launch {
            generateInstallationId()
            setUpSentry()
        }

        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this)
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

        FirebaseHelper.getInstance(this)
        NightModeHelper.getInstance(this)
        IssueCountHelper.getInstance(this)
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
        SentryProvider.initSentry(this@AbstractTazApplication)

        val user = User()
        user.id = authHelper.installationId.get()
        Sentry.setUser(user)
    }
}


fun Activity.getTazApplication(): AbstractTazApplication = application as AbstractTazApplication
fun Fragment.getTazApplication(): AbstractTazApplication = requireActivity().getTazApplication()