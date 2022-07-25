package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.api.models.ResourceInfoKey
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.data.DataService
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.ActivityWelcomeBinding
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.AppWebChromeClient
import de.taz.app.android.util.Log
import de.taz.app.android.util.showConnectionErrorDialog
import de.taz.app.android.util.showFatalErrorDialog
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val START_HOME_ACTIVITY = "START_HOME_ACTIVITY"

class WelcomeActivity : ViewBindingActivity<ActivityWelcomeBinding>() {

    private val log by Log

    private lateinit var storageService: StorageService
    private lateinit var dataService: DataService
    private lateinit var contentService: ContentService
    private lateinit var fileEntryRepository: FileEntryRepository
    private var resourceInfoRepository: ResourceInfoRepository? = null
    private var startHomeActivity = false

    private val generalDataStore by lazy { GeneralDataStore.getInstance(applicationContext) }

    private val welcomeSlidesHtmlFile = "welcomeSlidesAndroid.html"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startHomeActivity = intent.getBooleanExtra(START_HOME_ACTIVITY, false)

        storageService = StorageService.getInstance(applicationContext)
        resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)
        dataService = DataService.getInstance(applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        contentService = ContentService.getInstance(applicationContext)

        viewBinding.apply {
            buttonClose.setOnClickListener {
                log.debug("welcome screen close clicked")
                setFirstTimeStart()
                done()
            }

            webViewFullscreenContent.apply {
                webViewClient = WebViewClient()
                webChromeClient = AppWebChromeClient(::hideLoadingScreen)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
            }
        }
        lifecycleScope.launch {
            ensureResourceInfoIsDownloadedAndShowWelcomeSlides()
        }

    }

    private fun setFirstTimeStart() {
        lifecycleScope.launch { generalDataStore.hasSeenWelcomeScreen.set(true) }
    }

    override fun onBackPressed() {
        // flag SETTINGS_FIRST_TIME_APP_STARTS will not be set to true
        done()
    }

    private suspend fun ensureResourceInfoIsDownloadedAndShowWelcomeSlides() {
        try {
            contentService.downloadToCache(ResourceInfoKey(-1))
            showWelcomeSlides()
        } catch (e: CacheOperationFailedException) {
            showConnectionErrorDialog()
        } catch (e: HTMLFileNotFoundException) {
            val hint = "Html file for data policy not found"
            log.error(hint)
            Sentry.captureException(e, hint)
            showFatalErrorDialog()
        }
    }


    private suspend fun showWelcomeSlides() = withContext(Dispatchers.IO) {
        fileEntryRepository.get(welcomeSlidesHtmlFile)?.let {
            storageService.getFileUri(it)
        }?.let {
            withContext(Dispatchers.Main) {
                viewBinding.webViewFullscreenContent.loadUrl(it)
            }
        } ?: run {
            throw HTMLFileNotFoundException("Data policy html file ($welcomeSlidesHtmlFile) not found in database")
        }
    }

    private fun hideLoadingScreen() {
        this.runOnUiThread {
            viewBinding.welcomeLoadingScreen.root.apply {
                animate()
                    .alpha(0f)
                    .withEndAction {
                        visibility = View.GONE
                    }
                    .duration = LOADING_SCREEN_FADE_OUT_TIME
            }
        }
    }

    private fun done() {
        if (startHomeActivity) {
            startMainActivity()
        } else {
            finish()
        }
    }

    private fun startMainActivity() {
        MainActivity.start(this, Intent.FLAG_ACTIVITY_NO_ANIMATION)
        finish()
    }
}