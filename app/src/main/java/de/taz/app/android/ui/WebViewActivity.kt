package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.WEBVIEW_HTML_FILE
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.data.DataService
import de.taz.app.android.databinding.ActivityWebviewBinding
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.webview.AppWebChromeClient
import de.taz.app.android.util.Log
import de.taz.app.android.util.showFatalErrorDialog
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class WebViewActivity : ViewBindingActivity<ActivityWebviewBinding>() {

    private lateinit var storageService: StorageService
    private var resourceInfoRepository: ResourceInfoRepository? = null

    private var downloadedObserver: Observer<Boolean>? = null
    private var isDownloadedLiveData: LiveData<Boolean>? = null

    private lateinit var dataService: DataService
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var toastHelper: ToastHelper

    private val log by Log

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storageService = StorageService.getInstance(applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)
        dataService = DataService.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)

        viewBinding.apply {
            webViewButton.setOnClickListener {
                finish()
            }

            webViewFullscreenContent.apply {
                webChromeClient = AppWebChromeClient(::hideLoadingScreen)
                settings.javaScriptEnabled = true
            }
        }

        intent.extras?.getString(WEBVIEW_HTML_FILE)?.let {
            try {
                showHtmlFile(it)
            } catch (e: HTMLFileNotFoundException) {
                val hint = "Html file $it not found"
                log.error(hint)
                Sentry.captureException(e, hint)
                showFatalErrorDialog()
            }
        } ?: run {
            throw IllegalArgumentException("WebViewActivity needs to be started with WEBVIEW_HTML_FILE extra in intent")
        }

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
                throw HTMLFileNotFoundException(
                    "Could not find html file ${
                        intent.extras?.getString(
                            WEBVIEW_HTML_FILE
                        )
                    }"
                )
            }
        }
    }

    private fun hideLoadingScreen() {
        this.runOnUiThread {
            viewBinding.webViewLoadingScreen.root.animate().alpha(0f).duration = LOADING_SCREEN_FADE_OUT_TIME
        }
    }

    override fun onDestroy() {
        isDownloadedLiveData?.let { liveData ->
            downloadedObserver?.let { observer ->
                Transformations.distinctUntilChanged(liveData).removeObserver(observer)
            }
        }
        super.onDestroy()
    }
}