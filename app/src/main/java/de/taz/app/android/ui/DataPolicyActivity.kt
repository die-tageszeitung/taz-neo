package de.taz.app.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.data.DataService
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.singletons.SETTINGS_DATA_POLICY_ACCEPTED
import de.taz.app.android.singletons.SETTINGS_FIRST_TIME_APP_STARTS
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.AppWebChromeClient
import de.taz.app.android.util.Log
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import kotlinx.android.synthetic.main.activity_data_policy.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val FINISH_ON_CLOSE = "FINISH ON CLOSE"

class DataPolicyActivity : AppCompatActivity() {

    private val log by Log
    private val dataPolicyPage = "welcomeSlidesDataPolicy.html"

    private var fileHelper: FileHelper? = null
    private lateinit var dataService: DataService

    private var finishOnClose = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        finishOnClose = intent.getBooleanExtra(FINISH_ON_CLOSE, false)

        fileHelper = FileHelper.getInstance(applicationContext)
        dataService = DataService.getInstance(applicationContext)

        setContentView(R.layout.activity_data_policy)

        data_policy_accept_button?.setOnClickListener {
            acceptDataPolicy()
            if (finishOnClose) {
                finish()
            } else {
                if (isFirstTimeStart()) {
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

        data_policy_fullscreen_content.apply {
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                }
            }
            webChromeClient = AppWebChromeClient(::hideLoadingScreen)

            fileHelper?.getFileDirectoryUrl(this.context)?.let { fileDir ->
                val file = File("$fileDir/$RESOURCE_FOLDER/$dataPolicyPage")
                lifecycleScope.launch(Dispatchers.IO) {
                    ensureResourceInfoIsDownloadedAndShow(file.path)
                }
            }
        }
    }

    private fun acceptDataPolicy() {
        val tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

        SharedPreferenceBooleanLiveData(
            tazApiCssPreferences, SETTINGS_DATA_POLICY_ACCEPTED, true
        ).postValue(true)
    }

    private fun isFirstTimeStart(): Boolean {
        val tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)
        return !tazApiCssPreferences.contains(SETTINGS_FIRST_TIME_APP_STARTS)
    }

    private suspend fun ensureResourceInfoIsDownloadedAndShow(filePath: String) {

        val resourceInfo = dataService.getResourceInfo(retryOnFailure = true)
        try {
            dataService.ensureDownloaded(resourceInfo)
        } catch (e: CancellationException) {
            throw e
        }
        withContext(Dispatchers.Main) {
            data_policy_fullscreen_content.loadUrl(filePath)
        }
    }

    private fun hideLoadingScreen() {
        this.runOnUiThread {
            data_policy_loading_screen?.animate()?.alpha(0f)?.duration =
                LOADING_SCREEN_FADE_OUT_TIME
        }
    }
}
