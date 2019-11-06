package de.taz.app.android.ui.webview

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.util.Log

const val PREFERENCES_TAZAPI = "preferences_tazapi"

class TazApiJS (val view: WebViewContract.View) {

    val context = view.getMainView()?.getApplicationContext()

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
    fun pageReady(percentage: Int, position: Int) {
        log.debug("pageReady $percentage $position")
        view.getWebViewDisplayable()?.let {
            if (it is Article) {
                ArticleRepository.getInstance().saveScrollingPosition(it, percentage, position)
            }
        }
        log.debug("pageReady2")
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
