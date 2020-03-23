package de.taz.app.android.ui.webview

import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

const val TAZ_API_JS = "ANDROIDAPI"
const val PREFERENCES_TAZAPI = "preferences_tazapi"

class TazApiJS constructor(webViewFragment: WebViewFragment<*>) {

    private val log by Log

    private val webViewFragmentReference = WeakReference(webViewFragment)

    private val webViewFragment
        get() = webViewFragmentReference.get()

    private val mainActivity
        get() = webViewFragment?.getMainActivity()

    private val applicationContext
        get() = mainActivity?.applicationContext

    private val displayable
        get() = webViewFragment?.viewModel?.displayable

    @JavascriptInterface
    fun getConfiguration(name: String): String {
        log.debug("getConfiguration $name")
        val sharedPreferences =
            applicationContext?.getSharedPreferences(PREFERENCES_TAZAPI, Context.MODE_PRIVATE)
        return sharedPreferences?.getString(name, "") ?: ""
    }

    @JavascriptInterface
    fun setConfiguration(name: String, value: String) {
        log.debug("setConfiguration $name: $value")
        val sharedPref =
            applicationContext?.getSharedPreferences(PREFERENCES_TAZAPI, Context.MODE_PRIVATE)
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
        webViewFragment?.viewModel?.displayable?.let {
            if (it is Article) {
                ArticleRepository.getInstance(applicationContext)
                    .saveScrollingPosition(it, percentage, position)
            }
        }
    }

    @JavascriptInterface
    fun nextArticle(position: Int = 0) {
        log.debug("nextArticle $position")
        mainActivity?.lifecycleScope?.launch(Dispatchers.IO) {
            displayable?.next()?.let { next ->
                mainActivity?.showInWebView(next.key, R.anim.slide_in_left, R.anim.slide_out_left)
            }
        }
    }

    @JavascriptInterface
    fun previousArticle(position: Int = 0) {
        log.debug("previousArticle $position")
        mainActivity?.lifecycleScope?.launch(Dispatchers.IO) {
            displayable?.previous()?.let { previous ->
                mainActivity?.showInWebView(previous.key, R.anim.slide_in_right, R.anim.slide_out_right)
            }
        }
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        log.debug("openUrl $url")
        // relevant for links in the title for instance

        mainActivity?.apply {
            lifecycleScope.launch(Dispatchers.IO) {
                if (url.endsWith(".html") && (url.startsWith("article") || url.startsWith("section"))) {
                    showInWebView(url)
                } else {
                    openExternally(url)
                }
            }
        }
    }

    private fun openExternally(url: String) {
        runIfNotNull(applicationContext, mainActivity) { applicationContext, mainActivity ->
            val color = ContextCompat.getColor(applicationContext, R.color.colorAccent)
            CustomTabsIntent.Builder().setToolbarColor(color).build().apply {
                launchUrl(mainActivity, Uri.parse(url))
            }
        }
    }

}
