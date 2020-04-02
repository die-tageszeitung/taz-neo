package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.singletons.SETTINGS_FIRST_TIME_APP_STARTS
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import kotlinx.android.synthetic.main.activity_welcome.*

class WelcomeActivity : AppCompatActivity() {

    private val log by Log

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
            webChromeClient = WebChromeClient()

            settings.javaScriptEnabled = true
            val fileDir = FileHelper.getInstance().getFileDirectoryUrl(this.context)
            loadUrl("$fileDir/$RESOURCE_FOLDER/welcomeSlides.html")
        }
    }

    private fun setFirstTimeStart() {
        val tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

        SharedPreferenceBooleanLiveData(
            tazApiCssPreferences, SETTINGS_FIRST_TIME_APP_STARTS, true
        ).postValue(true)
    }
}