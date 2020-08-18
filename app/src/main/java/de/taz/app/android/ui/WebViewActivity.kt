package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_HTML_FILE
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.ui.webview.AppWebChromeClient
import kotlinx.android.synthetic.main.activity_webview.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class WebViewActivity : AppCompatActivity() {

    private var fileHelper: FileHelper? = null
    private var resourceInfoRepository: ResourceInfoRepository? = null

    private var downloadedObserver: Observer<Boolean>? = null
    private var isDownloadedLiveData: LiveData<Boolean>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileHelper = FileHelper.getInstance(applicationContext)
        resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)

        setContentView(R.layout.activity_webview)

        web_view_button.setOnClickListener {
            finish()
        }

        val htmlFile = intent.extras?.getString(WEBVIEW_HTML_FILE)

        web_view_fullscreen_content.apply {
            webViewClient = WebViewClient()
            webChromeClient = AppWebChromeClient(::hideLoadingScreen)

            settings.javaScriptEnabled = true
            val fileDir = fileHelper?.getFileDirectoryUrl(this.context)
            val file = File("$fileDir/$RESOURCE_FOLDER/$htmlFile")
            ensureResourceInfoIsDownloadedAndShow(file.path)
        }
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
                    isDownloadedLiveData?.observeDistinct(this@WebViewActivity, it)
                }
            }
        }

    private fun hideLoadingScreen() {
        this.runOnUiThread {
            web_view_loading_screen?.animate()?.alpha(0f)?.duration = LOADING_SCREEN_FADE_OUT_TIME
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