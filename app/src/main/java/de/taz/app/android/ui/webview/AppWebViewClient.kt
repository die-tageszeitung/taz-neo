package de.taz.app.android.ui.webview

import android.content.Context
import android.net.Uri
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import de.taz.app.android.R
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URLDecoder

interface AppWebViewClientCallBack {
    fun onLinkClicked(displayableKey: String)
    fun onPageFinishedLoading()
}

class AppWebViewClient(
    applicationContext: Context,
    private val callBack: AppWebViewClientCallBack
) : WebViewClient() {

    private val log by Log
    private val storageService = StorageService.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    @Deprecated("Deprecated in Java - But needed for Android versions pre 7.0.0")
    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(webView: WebView?, url: String?): Boolean {
        return shouldOverride(webView, url) || super.shouldOverrideUrlLoading(webView, url)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(
        webView: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        return shouldOverride(webView, request?.url.toString())
                || super.shouldOverrideUrlLoading(webView, request)
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
                return urlString.startsWith("file:///") || checkIfWeHaveLocally(url)
            }
        }
        return false
    }

    private fun createNewFragment(url: String?): Boolean {
        url?.let {
            return when {
                it.startsWith("file:///") && it.contains("section") && it.endsWith(".html") -> {
                    callBack.onLinkClicked(url.split("/").last())
                    true
                }
                it.startsWith("file:///") && it.contains("art") && it.endsWith(".html") -> {
                    callBack.onLinkClicked(url.split("/").last())
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
        CustomTabsIntent.Builder().setToolbarColor(color).build().apply {
            launchUrl(webView.context, Uri.parse(url))
        }
    }

    /**
     * intercept links to "resources/" and "global/" and point them to the correct directories
     */
    private fun overrideInternalLinks(view: WebView?, url: String?): String? {
        view?.let {
            url?.let {
                val fileName = url.substring(url.lastIndexOf('/') + 1, url.length)
                val fileEntry = runBlocking {  fileEntryRepository.get(fileName) }
                fileEntry?.let { return storageService.getFileUri(it) }
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
        val data = File(newUrl.toString().removePrefix("file://"))

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
            val hint = "trying to open non-existent file $newUrl"
            log.error(hint)
            Sentry.captureException(e, hint)
            null
        }
    }

    private fun checkIfWeHaveLocally(url: String): Boolean = runBlocking(Dispatchers.IO) {
        val fileName = url.substring(url.lastIndexOf('/') + 1, url.length)
        fileEntryRepository.get(fileName) != null
    }

    override fun onPageFinished(webview: WebView, url: String) {
        super.onPageFinished(webview, url)
        callBack.onPageFinishedLoading()
    }
}
