package de.taz.app.android.ui.webview

import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import de.taz.app.android.R
import de.taz.app.android.util.Log

const val PREFERENCES_TAZAPI = "preferences_tazapi"

class TazApiJS constructor(private val applicationContext: Context) {

    private val log by Log

    @JavascriptInterface
    fun getConfiguration(name: String): String {
        log.debug("getConfiguration $name")
        val sharedPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPI, Context.MODE_PRIVATE)
        return sharedPreferences?.getString(name, "") ?: ""
    }

    @JavascriptInterface
    fun setConfiguration(name: String, value: String) {
        log.debug("setConfiguration $name: $value")
        val sharedPref = applicationContext.getSharedPreferences(PREFERENCES_TAZAPI, Context.MODE_PRIVATE)
        sharedPref?.apply {
            with(sharedPref.edit()) {
                putString(name, value)
                commit()
            }
        }
    }

    @JavascriptInterface
    fun pageReady(percentage: Int, position: Int) {
        log.debug("pageReady $percentage $position")
        /*view.getWebViewDisplayable()?.let {
            if (it is Article) {
                ArticleRepository.getInstance().saveScrollingPosition(it, percentage, position)
            }
        }*/
    }

    @JavascriptInterface
    fun nextArticle(position: Int = 0) {
        log.debug("nextArticle $position")
        /*view.getMainView()?.let { mainView ->
            mainView.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                view.getWebViewDisplayable()?.next()?.let { next ->
                    mainView.showInWebView(next, R.anim.slide_in_left, R.anim.slide_out_left)
                }
            }
        }*/
    }

    @JavascriptInterface
    fun previousArticle(position: Int = 0) {
        log.debug("previousArticle $position")
        /*view.getMainView()?.let { mainView ->
            mainView.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                view.getWebViewDisplayable()?.previous()?.let { previous ->
                    mainView.showInWebView(previous, R.anim.slide_in_right, R.anim.slide_out_right)
                }
            }
        }*/
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        log.debug("openUrl $url")
        // relevant for links in the title for instance

        /*view.getMainView()?.apply {
            getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                ArticleRepository.getInstance().get(url)?.let {
                    showInWebView(it)
                } ?: SectionRepository.getInstance().get(url)?.let {
                    showInWebView(it)
                } ?: openExternally(url)
            }
        }*/
    }

    private fun openExternally(url: String) {
        applicationContext.let {
            val color = ContextCompat.getColor(it, R.color.colorAccent)
            CustomTabsIntent.Builder().setToolbarColor(color).build().apply {
                launchUrl(it, Uri.parse(url))
            }
        }
    }

}
