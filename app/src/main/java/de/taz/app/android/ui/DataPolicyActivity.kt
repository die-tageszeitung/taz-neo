package de.taz.app.android.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.models.ResourceInfoKey
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.ActivityDataPolicyBinding
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.AppWebChromeClient
import de.taz.app.android.util.Log
import de.taz.app.android.util.showConnectionErrorDialog
import de.taz.app.android.util.showFatalErrorDialog
import io.sentry.Sentry
import kotlinx.coroutines.*

const val FINISH_ON_CLOSE = "FINISH ON CLOSE"

class DataPolicyActivity : ViewBindingActivity<ActivityDataPolicyBinding>() {

    private val log by Log
    private val dataPolicyPageName = "welcomeSlidesDataPolicy.html"

    private lateinit var storageService: StorageService
    private lateinit var contentService: ContentService
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var toastHelper: ToastHelper

    private var finishOnClose = false

    private val generalDataStore by lazy {
        GeneralDataStore.getInstance(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishOnClose = intent.getBooleanExtra(FINISH_ON_CLOSE, false)

        storageService = StorageService.getInstance(applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        contentService = ContentService.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)

        viewBinding.apply {
            dataPolicyAcceptButton.setOnClickListener {
                acceptDataPolicy()
                if (finishOnClose) {
                    finish()
                } else {
                    if (hasSeenWelcomeScreen()) {
                        log.debug("start welcome activity")
                        val intent = Intent(applicationContext, WelcomeActivity::class.java)
                        intent.putExtra(START_HOME_ACTIVITY, true)
                        intent.flags =
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(Intent(intent))
                    } else {
                        log.debug("start main activity")
                        MainActivity.start(applicationContext, Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                }
            }

            dataPolicyFullscreenContent.apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        try {
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            toastHelper.showToast(R.string.toast_no_email_client)
                            log.warn("Could not open email activity: ${e.localizedMessage}")
                        }
                        return true
                    }
                }
                webChromeClient = AppWebChromeClient(::hideLoadingScreen)

                lifecycleScope.launch {
                    ensureResourceInfoIsDownloadedAndShowDataPolicy()
                }
            }
        }
    }

    private fun acceptDataPolicy() {
        applicationScope.launch {
            generalDataStore.dataPolicyAccepted.set(true)
        }
    }

    private fun hasSeenWelcomeScreen(): Boolean = runBlocking {
        !generalDataStore.hasSeenWelcomeScreen.get()
    }

    private suspend fun ensureResourceInfoIsDownloadedAndShowDataPolicy() =
        withContext(Dispatchers.Main) {
            try {
                contentService.downloadToCache(ResourceInfoKey(-1))
                showDataPolicy()
            } catch (e: CacheOperationFailedException) {
                showConnectionErrorDialog()
                Sentry.captureException(e)
            } catch (e: HTMLFileNotFoundException) {
                val hint = "Html file for data policy not found"
                log.error(hint)
                Sentry.captureException(e, hint)
                showFatalErrorDialog()
            }
        }

    private suspend fun showDataPolicy() {
        fileEntryRepository.get(dataPolicyPageName)?.let {
            storageService.getFileUri(it)
        }?.let {
            withContext(Dispatchers.Main) {
                viewBinding.dataPolicyFullscreenContent.loadUrl(it)
            }
        } ?: run {
            throw HTMLFileNotFoundException("Data policy html file ($dataPolicyPageName) not found in database")
        }
    }

    private fun hideLoadingScreen() {
        this.runOnUiThread {
            viewBinding.dataPolicyLoadingScreen.root.apply {
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
