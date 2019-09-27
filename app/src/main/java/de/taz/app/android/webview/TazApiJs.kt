package de.taz.app.android.webview

import android.content.Context
import android.webkit.JavascriptInterface
import de.taz.app.android.util.Log
import android.widget.Toast
import java.net.URL

class TazApiJs(private val context: Context) {

    private val log by Log

    @JavascriptInterface
    fun test(s: String) {
        Toast.makeText(context, s, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun getConfiguration(parameter: Array<String>) {
        log.debug("getConfiguration")
    }

    @JavascriptInterface
    fun getValue(parameter: String, cb: String) {
        log.debug("getValue whit params: $parameter and $cb")
    }

    @JavascriptInterface
    fun setConfiguration(parameter: String, value: Unit) {
        log.debug("setConfiguration")
    }

    @JavascriptInterface
    fun pageReady() {
        log.debug("pageReady")
    }

    @JavascriptInterface
    fun nextArticle(position: Int = 0) {
        log.debug("nextArticle")
    }

    @JavascriptInterface
    fun previousArtcile(position: Int = 0) {
        log.debug("previousArticle")
    }

    @JavascriptInterface
    fun openUrl(url: URL) {
        log.debug("openUrl")
    }
}
