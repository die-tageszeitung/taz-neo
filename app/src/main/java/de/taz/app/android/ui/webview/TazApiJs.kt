package de.taz.app.android.ui.webview

import android.webkit.JavascriptInterface
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log

class TazApiJs(val mainActivity: MainActivity) {

    private val log by Log

    @JavascriptInterface
    fun getConfiguration(name: String) {
        log.debug("getConfiguration $name")
    }

    @JavascriptInterface
    fun setConfiguration(name: String, value: Unit) {
        log.debug("setConfiguration $name: $value")
    }

    @JavascriptInterface
    fun pageReady(percentage: String, position: String) {
        log.debug("pageReady $percentage $position")
    }

    @JavascriptInterface
    fun nextArticle(position: Int = 0) {
        log.debug("nextArticle $position")
    }

    @JavascriptInterface
    fun previousArticle(position: Int = 0) {
        log.debug("previousArticle $position")
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        log.debug("openUrl $url")
    }
}
