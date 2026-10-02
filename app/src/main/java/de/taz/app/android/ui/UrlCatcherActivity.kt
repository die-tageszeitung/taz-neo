package de.taz.app.android.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.StartupActivity
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.ui.main.MainActivity.Companion.start
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class UrlCatcherActivity : StartupActivity() {

    private val log by Log

    private val generalDataStore by lazy { GeneralDataStore.getInstance(applicationContext) }
    private val authHelper by lazy { AuthHelper.getInstance(applicationContext) }
    private val apiService by lazy { ApiService.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val action: String? = intent?.action
        val data: Uri? = intent?.data

        if (Intent.ACTION_VIEW == action && data != null) {
            lifecycleScope.launch {
                try {
                    tryToStartAppFor(data)
                } catch (e: CanNotShowInternallyException) {
                    log.debug("Can not show $data", e)
                    showExternal(data)
                }
            }
        }
    }

    private suspend fun tryToStartAppFor(data: Uri) {
        if (shouldOpenWithNewsApp(data)) {
            try {
                tryShowWithNewsApp(data)
            } catch (_: ActivityNotFoundException) {
                // do nothing - try to open in this app
            }
        }

        if (!data.toString().contains("epaper.taz.de") && !authHelper.isLoggedIn()) {
            throw CanNotShowInternallyException("user not logged in and not epaper")
        }

        val mediaSyncId =
            data.toString().split("/!").getOrNull(1)
                ?: throw CanNotShowInternallyException("no mediaSyncId found in $data")

        val (date, displayableKey) = apiService.getDateAndDisplayableKeyForMediaSyncId(
            mediaSyncId
        ) ?: throw CanNotShowInternallyException("did not get a date for mediaSyncId $mediaSyncId")

        displayableKey
            ?: throw CanNotShowInternallyException("no displayable key for mediaSyncId $mediaSyncId")

        if (generalDataStore.pdfMode.get()) {
            start(
                this@UrlCatcherActivity,
                IssuePublicationWithPages("taz", date),
                displayableKey
            )
        } else {
            start(
                this@UrlCatcherActivity,
                IssuePublication("taz", date),
                displayableKey
            )
        }
    }

    private fun shouldOpenWithNewsApp(uri: Uri): Boolean {
        // TODO: pattern match etc
        return false
    }

    /**
     * either show in the news app - or in the browser
     */
    private fun showExternal(uri: Uri) {
        try {
            tryShowWithNewsApp(uri)
        } catch (_: ActivityNotFoundException) {
            openInBrowser(uri)
        }
    }

    @Throws(ActivityNotFoundException::class)
    private fun tryShowWithNewsApp(uri: Uri) {
        val packageName = "de.taz.taz.newsapp"

        startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply {
                `package` = packageName
            }
        )
    }

    private fun openInBrowser(uri: Uri) {
            val browserIntent = Intent(Intent.ACTION_VIEW, "https://".toUri())

        // Find which package is currently assigned as the default browser
        val resolveInfo = packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)

        if (resolveInfo != null) {
            val defaultBrowserPackage = resolveInfo.activityInfo.packageName

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                `package` = defaultBrowserPackage // Explicitly locks the intent to the default browser
            }
            startActivity(intent)
        } else {
            // Fallback if no default browser is set (rare)
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    class CanNotShowInternallyException(message: String) : Exception(message)

}