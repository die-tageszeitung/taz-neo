package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
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

class WelcomeActivity : AppCompatActivity() {

    private val log by Log
    fun getLifecycleOwner(): LifecycleOwner = this

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        button_close.setOnClickListener {
            log.debug("welcome screen close clicked")
            setFirstTimeStart()
            startActivity(Intent(applicationContext, MainActivity::class.java))
        }

        web_view_fullscreen_content.apply {
            webViewClient = WebViewClient()
            webChromeClient = AppWebChromeClient(::hideLoadingScreen)

            settings.javaScriptEnabled = true
            val fileDir = FileHelper.getInstance(applicationContext).getFileDirectoryUrl(this.context)
            val file = File("$fileDir/$RESOURCE_FOLDER/welcomeSlides.html")
            lifecycleScope.launch(Dispatchers.IO) {
                ensureResourceInfoIsDownloadedAndShow(file.path)
            }
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
        startActivity(Intent(applicationContext, MainActivity::class.java))
    }

    private suspend fun ensureResourceInfoIsDownloadedAndShow(filePath : String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val isDownloadedLiveData =
                ResourceInfoRepository.getInstance(applicationContext).get()?.isDownloadedLiveData()

            withContext(Dispatchers.Main) {
                isDownloadedLiveData?.observeDistinct(
                    getLifecycleOwner(),
                    Observer { isDownloaded ->
                        if (isDownloaded) {
                            web_view_fullscreen_content.loadUrl(filePath)
                        }
                    }
                )
            }
        }
    }

    private fun hideLoadingScreen() {
        this.runOnUiThread {
            welcome_loading_screen.visibility = View.GONE
        }
    }
}