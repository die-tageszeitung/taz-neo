package de.taz.app.android.ui.webview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import de.taz.app.android.DISPLAYABLE_NAME
import de.taz.app.android.R
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.ImagePagerActivity
import de.taz.app.android.util.Json
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


const val TAZ_API_JS = "ANDROIDAPI"
const val PREFERENCES_TAZAPI = "preferences_tazapi"
const val IMAGE_NAME = "image_name"

class TazApiJS constructor(private val webViewFragment: WebViewFragment<*, out WebViewViewModel<*>, out ViewBinding>) {

    private val log by Log

    private val applicationContext
        get() = webViewFragment.requireContext().applicationContext

    private val displayable
        get() = webViewFragment.viewModel.displayable

    @JavascriptInterface
    fun getConfiguration(name: String): String {
        log.verbose("getConfiguration $name")
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
                apply()
            }
        }
    }

    @JavascriptInterface
    fun pageReady(percentage: Int, position: Int) {
        log.debug("pageReady $percentage $position")
        /* This function is currently not used by the android app, as it does not scroll within the WebView
           Instead [ViewerStateRepository] is used to store the apps scroll position.
        webViewFragment.viewModel.displayable?.let {
            if (it is Article) {
                applicationContext?.let { context ->
                    webViewFragment.applicationScope.launch {
                        ArticleRepository.getInstance(context)
                            .saveScrollingPosition(it.key, percentage, position)
                    }
                }
            }
        }
        */
    }

    @JavascriptInterface
    fun nextArticle(position: Int = 0) {
        log.verbose("nextArticle $position")
        webViewFragment.lifecycleScope.launch {
            displayable?.next(applicationContext)?.let { next ->
                webViewFragment.setDisplayable(next.key)
            }
        }
    }

    @JavascriptInterface
    fun previousArticle(position: Int = 0) {
        log.verbose("previousArticle $position")
        webViewFragment.lifecycleScope.launch {
            displayable?.previous(applicationContext)?.let { previous ->
                webViewFragment.setDisplayable(previous.key)
            }
        }
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        log.verbose("openUrl $url")
        // relevant for links in the title for instance

        webViewFragment.apply {
            lifecycleScope.launch {
                if (!tapLock) {
                    if (url.endsWith(".html") && (url.startsWith("art") || url.startsWith("section"))) {
                        setDisplayable(url)
                    } else {
                        openExternally(url)
                    }
                }
            }
        }
    }

    private fun openExternally(url: String) {
        val color = ContextCompat.getColor(applicationContext, R.color.colorAccent)
        try {
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder().setToolbarColor(color).build()
                )
                .build()
                .launchUrl(webViewFragment.requireActivity(), Uri.parse(url))
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

        if (!webViewFragment.tapLock) {
            val intent = Intent(applicationContext, ImagePagerActivity::class.java)
            intent.putExtra(DISPLAYABLE_NAME, displayable?.key)
            intent.putExtra(IMAGE_NAME, name)

            webViewFragment.requireActivity().startActivity(
                intent
            )
        }
    }

    /**
     * @return Array of article names encoded as JSON
     */
    @JavascriptInterface
    fun getBookmarkedArticleNames(articleNamesJson: String): String {
        val articleNames: List<String> = try {
            Json.decodeFromString(articleNamesJson)
        } catch (e: IllegalArgumentException) {
            log.warn("Could not decode articleNames passed from JS: $articleNamesJson", e)
            emptyList()
        }

        val bookmarkedArticleNames = runBlocking {
            webViewFragment.setupBookmarkHandling(articleNames)
        }
        return Json.encodeToString(bookmarkedArticleNames)
    }

    @JavascriptInterface
    fun setBookmark(articleName: String, isBookmarked: Boolean, showNotification: Boolean) {
        runBlocking {
            // Set the tap lock, so it will not additionally scroll (when tap to scroll is activated)
            webViewFragment.tapLock = true
            webViewFragment.onSetBookmark(articleName, isBookmarked, showNotification)
        }
    }

    /**
     * @return Array of article names encoded as JSON
     */
    @JavascriptInterface
    fun getEnqueuedArticleNames(articleNamesJson: String): String {
        val articleNamesWithPrefix: List<String> = try {
            Json.decodeFromString(articleNamesJson)
        } catch (e: IllegalArgumentException) {
            log.warn("Could not decode articleNames passed from JS: $articleNamesJson", e)
            emptyList()
        }

        val articleNames = articleNamesWithPrefix.map { it.replace("PlaylistAdd.","") }
        val enqueuedArticleNames = runBlocking {
            webViewFragment.setupEnqueuedHandling(articleNames)
        }
        return Json.encodeToString(enqueuedArticleNames)
    }

    @JavascriptInterface
    fun setEnqueued(articleName: String, isEnqueued: Boolean) {
        val correctArticleName = articleName.replace("PlaylistAdd.","")
        runBlocking {
            webViewFragment.onEnqueued(correctArticleName, isEnqueued)
        }
    }

    /**
     * Parse the given [contentWidth] String to an Integer.
     * Mostly it will be something like "1627.33px" and should pass then 1627
     * to the [MultiColumnLayoutReadyCallback.onMultiColumnLayoutReady].
     */
    @JavascriptInterface
    fun onMultiColumnLayoutReady(contentWidth: String?) {
        val parsedContentWidth = try {
            contentWidth?.replace("px", "")?.toFloat()
        } catch (e: NumberFormatException) {
            log.warn("Could not parse $contentWidth to int. Passing null")
            null
        }
        if (webViewFragment is MultiColumnLayoutReadyCallback) {
            webViewFragment.onMultiColumnLayoutReady(parsedContentWidth?.toInt())
        }
    }


    @JavascriptInterface
    fun logMissingJsFeature(name: String) {
        val message = "Missing JavaScript feature: $name"
        log.warn(message);
        SentryWrapper.captureMessage(message)
    }
}
