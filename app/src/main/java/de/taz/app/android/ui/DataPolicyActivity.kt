package de.taz.app.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.data.DataService
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.AppWebChromeClient
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_data_policy.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val FINISH_ON_CLOSE = "FINISH ON CLOSE"

class DataPolicyActivity : AppCompatActivity() {

    private val log by Log
    private val dataPolicyPageName = "welcomeSlidesDataPolicy.html"

    private var storageService: StorageService? = null
    private lateinit var dataService: DataService
    private lateinit var fileEntryRepository: FileEntryRepository

    private var finishOnClose = false

    private val generalDataStore by lazy {
        GeneralDataStore.getInstance(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishOnClose = intent.getBooleanExtra(FINISH_ON_CLOSE, false)

        storageService = StorageService.getInstance(applicationContext)
        dataService = DataService.getInstance(applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

        setContentView(R.layout.activity_data_policy)

        data_policy_accept_button?.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
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
                        val intent = Intent(applicationContext, MainActivity::class.java)

                        intent.flags =
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(Intent(intent))
                    }
                }
            }
        }

        data_policy_fullscreen_content.apply {
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                }
            }
            webChromeClient = AppWebChromeClient(::hideLoadingScreen)

            lifecycleScope.launch(Dispatchers.IO) {
                val resourceInfo = dataService.getResourceInfo(retryOnFailure = true)
                dataService.ensureDownloaded(resourceInfo)
                val dataPolicyPageFileEntry = fileEntryRepository.get(dataPolicyPageName)
                val filePath = dataPolicyPageFileEntry?.let {
                    storageService?.getFileUri(it)
                }
                filePath?.let {
                    ensureResourceInfoIsDownloadedAndShow(it)
                }
            }

        }
    }

    private suspend fun acceptDataPolicy() = generalDataStore.dataPolicyAccepted.set(true)

    private suspend fun hasSeenWelcomeScreen(): Boolean = !generalDataStore.hasSeenWelcomeScreen.get()

    private suspend fun ensureResourceInfoIsDownloadedAndShow(filePath: String) {

        val resourceInfo = dataService.getResourceInfo(retryOnFailure = true)
        dataService.ensureDownloaded(resourceInfo)

        withContext(Dispatchers.Main) {
            data_policy_fullscreen_content.loadUrl(filePath)
        }
    }

    private fun hideLoadingScreen() {
        this.runOnUiThread {
            data_policy_loading_screen?.animate()?.apply {
                alpha(0f)
                duration = LOADING_SCREEN_FADE_OUT_TIME
                withEndAction {
                    data_policy_loading_screen?.visibility = View.GONE
                }
            }
        }
    }
}
