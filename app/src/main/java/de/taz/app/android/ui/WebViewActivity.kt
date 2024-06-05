package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityWebviewBinding
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.webview.AppWebChromeClient
import de.taz.app.android.util.Log
import de.taz.app.android.util.showFatalErrorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class WebViewActivity : ViewBindingActivity<ActivityWebviewBinding>() {

    companion object {
        private const val WEBVIEW_HTML_FILE = "htmlFile"

        fun newIntent(packageContext: Context, htmlFile: String) = Intent(packageContext, WebViewActivity::class.java).apply {
            putExtra(WEBVIEW_HTML_FILE, htmlFile)
        }
    }


    private lateinit var storageService: StorageService
    private lateinit var resourceInfoRepository: ResourceInfoRepository
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    private val log by Log

    private val htmlFile: String
        get() = intent.extras?.getString(WEBVIEW_HTML_FILE)
            ?: throw IllegalArgumentException("WebViewActivity needs to be started with WEBVIEW_HTML_FILE extra in intent")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storageService = StorageService.getInstance(applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)
        tracker = Tracker.getInstance(applicationContext)

        // The AudioPlayer shall stop when we show full screen info views
        AudioPlayerService.getInstance(applicationContext).apply {
            dismissPlayer()
        }

        viewBinding.apply {
            buttonClose.setOnClickListener {
                finish()
            }

            webViewFullscreenContent.apply {
                webChromeClient = AppWebChromeClient(::hideLoadingScreen)
                settings.javaScriptEnabled = true
            }
        }

        try {
            showHtmlFile(htmlFile)
        } catch (e: HTMLFileNotFoundException) {
            log.warn("Html file $htmlFile not found", e)
            SentryWrapper.captureException(e)
            showFatalErrorDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        tracker.trackWebViewScreen(htmlFile)
    }

    private fun showHtmlFile(htmlFileKey: String) {
        lifecycleScope.launch {
            fileEntryRepository.get(htmlFileKey)?.let {
                storageService.getFileUri(it)
            }?.let {
                withContext(Dispatchers.Main) {
                    viewBinding.webViewFullscreenContent.loadUrl(it)
                }
            } ?: run {
                throw HTMLFileNotFoundException("Could not find html file $htmlFile")
            }
        }
    }

    private fun hideLoadingScreen() {
        this.runOnUiThread {
            viewBinding.webViewLoadingScreen.root.animate().alpha(0f).duration = LOADING_SCREEN_FADE_OUT_TIME
        }
    }
}