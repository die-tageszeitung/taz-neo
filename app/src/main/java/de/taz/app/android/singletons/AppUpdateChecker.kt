package de.taz.app.android.singletons

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.util.SingletonHolder

/**
 * Singleton to check whether an app update occurred.
 */
class AppUpdateChecker private constructor(
    applicationContext: Context,
    private val generalDataStore: GeneralDataStore = GeneralDataStore.getInstance(applicationContext)
) {

    companion object : SingletonHolder<AppUpdateChecker, Context>(::AppUpdateChecker)

    val currentVersion: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        applicationContext.packageManager.getPackageInfo(
            applicationContext.packageName,
            PackageManager.PackageInfoFlags.of(0)
        ).longVersionCode
    } else {
        @Suppress("DEPRECATION")
        applicationContext.packageManager.getPackageInfo(
            applicationContext.packageName,
            0
        ).versionCode.toLong()
    }

    /**
     * Checks if the app was updated and updates the stored version code.
     * Note: This performs a side effect and should generally be called once per app launch session.
     */
    suspend fun checkAndMarkAppUpdated(): Boolean {
        val lastVersionCode =
            generalDataStore.lastVersionCode.get() // = 0L for versions below 2.1.2
        generalDataStore.lastVersionCode.set(currentVersion)

        // Pre 2.1.2 we additionally check if user had more than 10 app sessions
        val isFirstCheckForExistingUser =
            (lastVersionCode == 0L) && generalDataStore.appSessionCount.get() > 10L

        return (lastVersionCode != 0L && currentVersion > lastVersionCode || isFirstCheckForExistingUser)
    }
}