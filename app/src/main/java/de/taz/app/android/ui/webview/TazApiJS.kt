package de.taz.app.android.ui.webview

import android.content.Context
import android.webkit.JavascriptInterface
import de.taz.app.android.util.Log

const val PREFERENCES_TAZAPI = "preferences_tazapi"

class TazApiJS (val context: Context?) {

    private val log by Log

    @JavascriptInterface
    fun getConfiguration(name: String) : String {
        log.debug("getConfiguration $name")
        val sharedPreferences = context?.getSharedPreferences(PREFERENCES_TAZAPI, Context.MODE_PRIVATE)
        return sharedPreferences?.getString(name, "") ?: ""
    }

    @JavascriptInterface
    fun setConfiguration(name: String, value: String) {
        log.debug("setConfiguration $name: $value")
        val sharedPref = context?.getSharedPreferences(PREFERENCES_TAZAPI, Context.MODE_PRIVATE)
        sharedPref?.apply{
            with (sharedPref.edit()) {
                putString(name, value)
                commit()
            }
        }
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
