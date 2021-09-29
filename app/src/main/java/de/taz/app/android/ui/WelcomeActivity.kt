package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.data.DataService
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.AppWebChromeClient
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_welcome.*
import kotlinx.android.synthetic.main.activity_welcome.web_view_fullscreen_content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val START_HOME_ACTIVITY = "START_HOME_ACTIVITY"

class WelcomeActivity : AppCompatActivity() {

    private val log by Log

    private lateinit var storageService: StorageService
    private lateinit var dataService: DataService
    private lateinit var fileEntryRepository: FileEntryRepository
    private var resourceInfoRepository: ResourceInfoRepository? = null

    private var downloadedObserver: Observer<Boolean>? = null
    private var isDownloadedLiveData: LiveData<Boolean>? = null

    private var startHomeActivity = false

    private val generalDataStore by lazy { GeneralDataStore.getInstance(applicationContext) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startHomeActivity = intent.getBooleanExtra(START_HOME_ACTIVITY, false)

        storageService = StorageService.getInstance(applicationContext)
        resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)
        dataService = DataService.getInstance(applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

        setContentView(R.layout.activity_welcome)

        button_close.setOnClickListener {
            log.debug("welcome screen close clicked")
            setFirstTimeStart()
            done()
        }

        web_view_fullscreen_content.apply {
            webViewClient = WebViewClient()
            webChromeClient = AppWebChromeClient(::hideLoadingScreen)
            lifecycleScope.launch(Dispatchers.IO) {
                val resourceInfo = dataService.getResourceInfo(retryOnFailure = true)
                dataService.ensureDownloaded(resourceInfo)
                val welcomeSlidesFileEntry = fileEntryRepository.get("welcomeSlidesAndroid.html")

                withContext(Dispatchers.Main) {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = true
                }
                welcomeSlidesFileEntry?.let { storageService.getFileUri(it) }?.let {
                    ensureResourceInfoIsDownloadedAndShow(it)
                }
            }
        }
    }

    private fun setFirstTimeStart() = CoroutineScope(Dispatchers.IO).launch {
        generalDataStore.firstAppStart.update(true)
    }

    override fun onBackPressed() {
        // flag SETTINGS_FIRST_TIME_APP_STARTS will not be set to true
        done()
    }

    private fun ensureResourceInfoIsDownloadedAndShow(filePath: String) =
        lifecycleScope.launch(Dispatchers.IO) {
            val resourceInfo = dataService.getResourceInfo(retryOnFailure = true)
            dataService.ensureDownloaded(resourceInfo)
            withContext(Dispatchers.Main) {
                web_view_fullscreen_content.loadUrl(filePath)
            }
        }

    private fun hideLoadingScreen() {
        this.runOnUiThread {
            welcome_loading_screen?.animate()?.apply {
                alpha(0f)
                duration = LOADING_SCREEN_FADE_OUT_TIME
                withEndAction {
                    welcome_loading_screen?.visibility = View.GONE
                }
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
        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
        startActivity(intent)
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