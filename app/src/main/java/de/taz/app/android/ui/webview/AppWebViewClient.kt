package de.taz.app.android.ui.webview

import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import java.io.File
import android.net.Uri
import androidx.annotation.RequiresApi
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.WebViewDisplayable
import io.sentry.Sentry
import kotlinx.coroutines.*
import java.lang.Exception
import java.net.URLDecoder

class AppWebViewClient<DISPLAYABLE : WebViewDisplayable>(private val presenter: WebViewPresenter<DISPLAYABLE>) :
    WebViewClient() {

    private val log by Log
    private val fileHelper = FileHelper.getInstance()

    @SuppressWarnings("deprecation")
    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(webView: WebView?, url: String?): Boolean {
        return shouldOverride(webView, url) || super.shouldOverrideUrlLoading(webView, url)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(
        webView: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        return shouldOverride(webView, request?.url.toString()) || super.shouldOverrideUrlLoading(
            webView,
            request
        )
    }

    private fun shouldOverride(webView: WebView?, url: String?): Boolean {
        val decodedUrl = URLDecoder.decode(url.toString(), "UTF-8")
        return if (handleLinks(webView, decodedUrl)) {
            createNewFragment(decodedUrl)
        } else {
            handleExternally(webView, decodedUrl)
        }
    }

    /** internal links should be handled by the app, external ones - by a web browser
    this function checks whether a link is internal
     */
    private fun handleLinks(webView: WebView?, url: String?): Boolean {
        url?.let { urlString ->
            webView?.let {
                if (urlString.startsWith(fileHelper.getFileDirectoryUrl(webView.context))) {
                    return true
                }
            }
        }
        return false
    }

    private fun createNewFragment(url: String?): Boolean {
        url?.let {
            return when {
                it.startsWith("file:///") && it.contains("section") && it.endsWith(".html") -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val section = SectionRepository.getInstance().get(
                            url.split("/").last()
                        )
                        section?.let {
                            presenter.onLinkClicked(section)
                        }
                    }
                    true
                }
                it.startsWith("file:///") && it.contains("art") && it.endsWith(".html") -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val article = ArticleRepository.getInstance().get(
                            url.split("/").last()
                        )
                        article?.let {
                            presenter.onLinkClicked(article)
                        }
                    }
                    true
                }
                else -> false
            }
        }
        return false
    }

    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        return if (handleLinks(view, url)) {
            createCustomWebResourceResponse(view, url)
        } else {
            return null
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun shouldInterceptRequest(
        webView: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        request?.let {
            val url = Uri.decode(request.url.toString())
            if (handleLinks(webView, url)) {
                return createCustomWebResourceResponse(webView, url)
            }
        }
        return null
    }

    private fun handleExternally(webView: WebView?, url: String?): Boolean {
        webView?.let {
            url?.let {
                log.debug("handling $url externally")
                openInBrowser(webView, url)
            }
        }
        return true
    }

    private fun openInBrowser(webView: WebView, url: String) {
        val color = ContextCompat.getColor(webView.context, R.color.colorAccent)
        CustomTabsIntent.Builder().setToolbarColor(color).build().apply{
            launchUrl(webView.context, Uri.parse(url))
        }
    }

    /**
     * intercept links to "resources/" and "global/" and point them to the correct directories
     */
    private fun overrideInternalLinks(view: WebView?, url: String?): String? {
        view?.let {
            url?.let {
                val fileDir = fileHelper.getFileDirectoryUrl(view.context)

                var newUrl = url.replace(
                    "$fileDir/\\w+/\\d{4}-\\d{2}-\\d{2}/resources/".toRegex(),
                    "$fileDir/resources/"
                )
                newUrl = newUrl.replace(
                    "$fileDir/\\w+/\\d{4}-\\d{2}-\\d{2}/global/".toRegex(),
                    "$fileDir/global/"
                )

                return newUrl
            }

        }
        return url
    }

    /**
     * handle correctly different resource types
     * TODO not sure whether these are all possible resource types and whether all mimeTypes are correct
     */
    private fun createCustomWebResourceResponse(
        view: WebView?,
        url: String?
    ): WebResourceResponse? {
        val newUrl = overrideInternalLinks(view, url)
        val data = File(newUrl.toString().removePrefix("file:///"))
        log.debug("Intercepted Url is $url")

        return try {
            when {
                url.toString().endsWith(".css") ->
                    WebResourceResponse("text/css", "UTF-8", data.inputStream())
                url.toString().endsWith(".html") ->
                    WebResourceResponse("text/html", "UTF-8", data.inputStream())
                url.toString().endsWith(".js") ->
                    WebResourceResponse("application/javascript", "UTF-8", data.inputStream())
                url.toString().endsWith(".png") ->
                    WebResourceResponse("image/png", "binary", data.inputStream())
                url.toString().endsWith(".svg") ->
                    WebResourceResponse("image/svg+xml", "UTF-8", data.inputStream())
                url.toString().endsWith(".woff") ->
                    WebResourceResponse("font/woff", "binary", data.inputStream())
                else ->
                    WebResourceResponse("text/plain", "UTF-8", data.inputStream())
            }
        } catch (e: Exception) {
            log.error("trying to open non-existent file $url")
            Sentry.capture(e)
            null
        }
    }

    override fun onPageFinished(webview: WebView, url: String) {
        super.onPageFinished(webview, url)

        presenter.onPageFinishedLoading()
    }
}
