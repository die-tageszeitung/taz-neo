package de.taz.app.android

import android.app.Application
import android.os.Build
import android.os.StrictMode
import com.facebook.stetho.Stetho
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.IssueCountHelper
import de.taz.app.android.singletons.NightModeHelper
import de.taz.app.android.util.Log
import io.sentry.Sentry
import io.sentry.protocol.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

abstract class AbstractTazApplication : Application() {
    private val log by Log

    private lateinit var authHelper: AuthHelper

    override fun onCreate() {
        super.onCreate()
        authHelper = AuthHelper.getInstance(this)
        CoroutineScope(Dispatchers.IO).launch {
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

    private suspend fun setUpSentry() {
        SentryProvider.initSentry(this@AbstractTazApplication)

        val user = User()
        user.id = authHelper.installationId.get()
        Sentry.setUser(user)
    }
}
