package de.taz.app.android.ui.webview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.DISPLAYABLE_NAME
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.ImagePagerActivity
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class SearchTazApiJS constructor(private val fragment: Fragment) {

    private val log by Log

    private val applicationContext
        get() = fragment.requireActivity().applicationContext

    @JavascriptInterface
    fun getConfiguration(name: String): String {
        log.verbose("getConfiguration $name")
        val sharedPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPI, Context.MODE_PRIVATE)
        return sharedPreferences?.getString(name, "") ?: ""
    }

    @JavascriptInterface
    fun setConfiguration(name: String, value: String) {
        log.debug("setConfiguration $name: $value")
        val sharedPref =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPI, Context.MODE_PRIVATE)
        sharedPref?.apply {
            with(sharedPref.edit()) {
                putString(name, value)
                apply()
            }
        }
    }

    @JavascriptInterface
    fun pageReady(percentage: Int, position: Int) {
        log.debug("pageReady $percentage $position")
    }

    @JavascriptInterface
    fun nextArticle(position: Int = 0) {
        log.verbose("nextArticle $position")
    }

    @JavascriptInterface
    fun previousArticle(position: Int = 0) {
        log.verbose("previousArticle $position")
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        log.verbose("openUrl $url")
        openExternally(url)
    }

    private fun openExternally(url: String) {
            val color = ContextCompat.getColor(applicationContext, R.color.colorAccent)
            try {
                CustomTabsIntent.Builder().setToolbarColor(color).build().apply {
                    launchUrl(fragment.requireActivity(), Uri.parse(url))
                }
            } catch (e: ActivityNotFoundException) {
                val toastHelper =
                    ToastHelper.getInstance(applicationContext)
                if (url.startsWith("mailto:")) {
                    toastHelper.showToast(R.string.toast_no_email_client)
                } else {
                    toastHelper.showToast(R.string.toast_unknown_error)
                }
            }
    }

    @JavascriptInterface
    fun openImage(name: String) {
        log.verbose("openImage $name")

        val intent = Intent(applicationContext, ImagePagerActivity::class.java)
        intent.putExtra(IMAGE_NAME, name)

        fragment.requireActivity().startActivity(
            intent
        )
    }
}
