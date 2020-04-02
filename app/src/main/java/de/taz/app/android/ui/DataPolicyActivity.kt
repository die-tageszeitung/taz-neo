package de.taz.app.android.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.singletons.SETTINGS_DATA_POLICY_ACCEPTED
import de.taz.app.android.singletons.SETTINGS_FIRST_TIME_APP_STARTS
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import kotlinx.android.synthetic.main.activity_data_policy.*

class DataPolicyActivity : AppCompatActivity() {

    private val log by Log

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_policy)

        findViewById<Button>(R.id.data_policy_accept_button)
            .setOnClickListener {
                acceptDataPolicy()
                if (isFirstTimeStart()) {
                    log.debug("start welcome activity")
                    startActivity(Intent(this, WelcomeActivity::class.java))
                } else {
                    log.debug("start main activity")
                    startActivity(Intent(this, MainActivity::class.java))
                }
            }

        data_policy_fullscreen_content.apply {
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            val fileDir = FileHelper.getInstance().getFileDirectoryUrl(this.context)
            loadUrl("$fileDir/$RESOURCE_FOLDER/welcomeSlidesDataPolicy.html")
        }
    }

    private fun acceptDataPolicy() {
        val tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

        SharedPreferenceBooleanLiveData(
            tazApiCssPreferences, SETTINGS_DATA_POLICY_ACCEPTED ,true
        ).postValue(true)
    }

    private fun isFirstTimeStart(): Boolean {
        val tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)
        return !tazApiCssPreferences.contains(SETTINGS_FIRST_TIME_APP_STARTS)
    }
}
