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
import de.taz.app.android.WEBVIEW_HTML_FILE_DATA_POLICY
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

class DataPolicyActivity : ViewBindingActivity<ActivityDataPolicyBinding>() {

    private val log by Log
    private val dataPolicyPageName = WEBVIEW_HTML_FILE_DATA_POLICY

    private lateinit var storageService: StorageService
    private lateinit var contentService: ContentService
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var toastHelper: ToastHelper
    private lateinit var generalDataStore: GeneralDataStore
    // Note: we are not doing any tracking here, as users won't have had the chance to accept the data policy before

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storageService = StorageService.getInstance(applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        contentService = ContentService.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)
        generalDataStore = GeneralDataStore.getInstance(applicationContext)

        viewBinding.apply {
            dataPolicyAcceptButton.setOnClickListener {
                acceptDataPolicy()
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

            dataPolicyFullscreenContent.apply {
                webViewClient = object : WebViewClient() {
                    @Deprecated("Deprecated in Java")
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
                log.warn("Html file for data policy not found", e)
                Sentry.captureException(e)
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
            log.error("Data policy html file ($dataPolicyPageName) not found in database")
            viewBinding.dataPolicyAcceptButton.callOnClick()
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
