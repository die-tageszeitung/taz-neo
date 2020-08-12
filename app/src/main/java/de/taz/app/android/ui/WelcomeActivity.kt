package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.singletons.SETTINGS_FIRST_TIME_APP_STARTS
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.AppWebChromeClient
import de.taz.app.android.util.Log
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import kotlinx.android.synthetic.main.activity_welcome.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val START_HOME_ACTIVITY = "START_HOME_ACTIVITY"

class WelcomeActivity : AppCompatActivity() {

    private val log by Log

    private var fileHelper: FileHelper? = null
    private var resourceInfoRepository: ResourceInfoRepository? = null

    private var downloadedObserver: Observer<Boolean>? = null
    private var isDownloadedLiveData: LiveData<Boolean>? = null

    private var startHomeActivity = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startHomeActivity = intent.getBooleanExtra(START_HOME_ACTIVITY, false)

        fileHelper = FileHelper.getInstance(applicationContext)
        resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)

        setContentView(R.layout.activity_welcome)

        button_close.setOnClickListener {
            log.debug("welcome screen close clicked")
            setFirstTimeStart()
            done()
        }

        web_view_fullscreen_content.apply {
            webViewClient = WebViewClient()
            webChromeClient = AppWebChromeClient(::hideLoadingScreen)

            settings.javaScriptEnabled = true
            val fileDir = fileHelper?.getFileDirectoryUrl(this.context)
            val file = File("$fileDir/$RESOURCE_FOLDER/welcomeSlides.html")
            ensureResourceInfoIsDownloadedAndShow(file.path)
        }
    }

    private fun setFirstTimeStart() {
        val tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

        SharedPreferenceBooleanLiveData(
            tazApiCssPreferences, SETTINGS_FIRST_TIME_APP_STARTS, true
        ).postValue(true)
    }

    override fun onBackPressed() {
        // flag SETTINGS_FIRST_TIME_APP_STARTS will not be set to true
        done()
    }

    private fun ensureResourceInfoIsDownloadedAndShow(filePath: String) =
        lifecycleScope.launch(Dispatchers.IO) {
            isDownloadedLiveData =
                resourceInfoRepository?.get()?.isDownloadedLiveData(applicationContext)

            downloadedObserver = Observer { isDownloaded ->
                if (isDownloaded) {
                    web_view_fullscreen_content.loadUrl(filePath)
                }
            }
            withContext(Dispatchers.Main) {
                downloadedObserver?.let {
                    isDownloadedLiveData?.observeDistinct(this@WelcomeActivity, it)
                }
            }
        }

    private fun hideLoadingScreen() {
        this.runOnUiThread {
            welcome_loading_screen?.animate()?.alpha(0f)?.duration = LOADING_SCREEN_FADE_OUT_TIME
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