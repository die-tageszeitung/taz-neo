package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.api.models.ResourceInfoKey
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.databinding.ActivityWelcomeBinding
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.webview.AppWebChromeClient
import de.taz.app.android.util.Log
import de.taz.app.android.util.showConnectionErrorDialog
import de.taz.app.android.util.showFatalErrorDialog
import de.taz.app.android.sentry.SentryWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WelcomeActivity : ViewBindingActivity<ActivityWelcomeBinding>() {

    private val log by Log

    private lateinit var storageService: StorageService
    private lateinit var contentService: ContentService
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var resourceInfoRepository: ResourceInfoRepository
    private lateinit var tracker: Tracker

    private val welcomeSlidesHtmlFile = "welcomeSlidesAndroid.html"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storageService = StorageService.getInstance(applicationContext)
        resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        contentService = ContentService.getInstance(applicationContext)
        tracker = Tracker.getInstance(applicationContext)

        // The AudioPlayer shall stop when we show full screen info views
        AudioPlayerService.getInstance(applicationContext).apply {
            dismissPlayer()
        }

        viewBinding.apply {
            buttonClose.setOnClickListener {
                log.debug("welcome screen close clicked")
                finish()
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

    override fun onResume() {
        super.onResume()
        // Track as if this would be a regular webview
        tracker.trackWebViewScreen(welcomeSlidesHtmlFile)
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }

    private suspend fun ensureResourceInfoIsDownloadedAndShowWelcomeSlides() {
        try {
            contentService.downloadToCache(ResourceInfoKey(-1))
            showWelcomeSlides()
        } catch (e: CacheOperationFailedException) {
            showConnectionErrorDialog()
        } catch (e: HTMLFileNotFoundException) {
            log.warn("Html file for data policy not found", e)
            SentryWrapper.captureException(e)
            showFatalErrorDialog()
        }
    }


    private suspend fun showWelcomeSlides() {
        fileEntryRepository.get(welcomeSlidesHtmlFile)?.let {
            storageService.getFileUri(it)
        }?.let {
            withContext(Dispatchers.Main) {
                viewBinding.webViewFullscreenContent.loadUrl(it)
            }
        } ?: run {
            log.error("welcome slides html file ($welcomeSlidesHtmlFile) not found in database")
            viewBinding.buttonClose.callOnClick()
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

}