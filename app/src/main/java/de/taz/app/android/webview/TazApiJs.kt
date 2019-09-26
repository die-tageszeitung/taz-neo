package de.taz.app.android.webview

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.net.URL

class TazApiJs(private val context: Context) {

    @JavascriptInterface
    fun test(s: String) {
        Toast.makeText(context, s, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun getConfiguration(parameter: String) {}

    @JavascriptInterface
    fun setConfiguration(parameter: String, value: Unit) {}

    @JavascriptInterface
    fun pageReady() {
    }

    @JavascriptInterface
    fun nextArticle(position: Int = 0) {

    }

    @JavascriptInterface
    fun previousArtcile(position: Int = 0) {

    }

    @JavascriptInterface
    fun openUrl(url: URL) {}

}
