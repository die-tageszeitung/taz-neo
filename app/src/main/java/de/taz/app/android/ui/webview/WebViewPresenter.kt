package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MotionEvent
import android.webkit.WebChromeClient
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.download.DownloadService
import de.taz.app.android.download.RESOURCE_FOLDER
import de.taz.app.android.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Base64
import de.taz.app.android.util.Log
import de.taz.app.android.util.PreferencesHelper


const val TAZ_API_JS = "ANDROIDAPI"

abstract class WebViewPresenter<DISPLAYABLE : WebViewDisplayable> :
    BasePresenter<WebViewContract.View<DISPLAYABLE>, WebViewDataController<DISPLAYABLE>>(
        WebViewDataController<DISPLAYABLE>().javaClass
    ),
    WebViewContract.Presenter {

    private val log by Log

    override fun attach(view: WebViewContract.View<DISPLAYABLE>) {

        super.attach(view)
        view.getWebViewDisplayable()?.let {
            viewModel?.setWebViewDisplayable(it)
        } ?: view.setWebViewDisplayable(viewModel?.getWebViewDisplayable())
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        configureWebView()
        observeFile()
    }


    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun configureWebView() {
        getView()?.let { webContractView ->
            val tazApiJS = TazApiJS(webContractView)
            webContractView.getWebView().apply {
                webViewClient = AppWebViewClient(this@WebViewPresenter)
                webChromeClient = WebChromeClient()
                settings.javaScriptEnabled = true
                addJavascriptInterface(tazApiJS, TAZ_API_JS)
                setArticleWebViewCallback(this@WebViewPresenter)
            }
        }
    }

    private fun observeFile() {
        getView()?.let { view ->
            viewModel?.observeWebViewDisplayable(view.getLifecycleOwner()) { displayable ->
                displayable?.let {
                    ensureDownloadedAndShow(displayable)
                }
            }
        }
    }

    private fun ensureDownloadedAndShow(displayable: DISPLAYABLE) {
        getView()?.apply {
            getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                if (!displayable.isDownloaded()) {
                    getMainView()?.getApplicationContext()?.let {
                        DownloadService.download(it, displayable)
                    }
                }
            }

            displayable.isDownloadedLiveData().observe(
                getLifecycleOwner(),
                DisplayableDownloadedObserver(displayable)
            )
        }
    }


    override fun onLinkClicked(webViewDisplayable: WebViewDisplayable) {
        getView()?.getMainView()?.showInWebView(webViewDisplayable)
    }

    override fun onPageFinishedLoading() {
        getView()?.hideLoadingScreen()
    }

    /**
     * This method will help to re-inject css into the WebView upon changes
     * to the corresponding shared preferences
     */
    fun injectCss(sharedPreferences: SharedPreferences) {
        log.debug("Injecting css")
        val fileHelper = FileHelper.getInstance()
        val preferencesHelper = PreferencesHelper.getInstance()

        val nightModeCssFile = fileHelper.getFile("$RESOURCE_FOLDER/themeNight.css")
        val nightModeCssString = if (sharedPreferences.getBoolean("text_night_mode", false)) "@import \"$nightModeCssFile\";" else ""
        val fontSizePx = preferencesHelper.computeFontSize(sharedPreferences.getString("text_font_size", "100") ?: "100")
        val cssString = """
            $nightModeCssString
            html, body {
                font-size: ${fontSizePx}px;
            }
        """.trimIndent()
        val encoded = Base64.encodeToString(cssString.toByteArray(), Base64.NO_WRAP)
        log.debug("Injected css: $cssString")
        getView()?.getWebView()?.evaluateJavascript("(function() {tazApi.injectCss(\"$encoded\");})()", null)
    }

    override fun onScrollStarted() {
    }

    override fun onScrollFinished() {
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        return false
    }

    private inner class DisplayableDownloadedObserver(
        private val displayable: DISPLAYABLE
    ) : Observer<Boolean> {
        override fun onChanged(isDownloaded: Boolean?) {
            if (isDownloaded == true) {
                displayable.isDownloadedLiveData().removeObserver(this)
                getView()?.getLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                    displayable.getFile()?.let { file ->
                        withContext(Dispatchers.Main) {
                            getView()?.loadUrl("file://${file.absolutePath}")
                        }
                    }
                }
            }
        }
    }
}
