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
        return shouldOverride(webView, url)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(
        webView: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        return shouldOverride(webView, request?.url?.toString())
    }

    private fun shouldOverride(webView: WebView?, url: String?): Boolean {
        if (url == null) {
            log.info("Canceling loading a null url")
            return true
        }

        if (webView == null) {
            log.info("Canceling loading url=$url with a null webView")
            return true
        }
        
        return runBlocking {
            val decodedUrl = URLDecoder.decode(url, "UTF-8")
            if (handleLinks(decodedUrl)) {
                createNewFragment(decodedUrl)
            } else {
                openInBrowser(webView, url)
                true
            }
        }
    }

    /** internal links should be handled by the app, external ones - by a web browser
    this function checks whether a link is internal
     */
    private suspend fun handleLinks(url: String): Boolean {
        return url.startsWith("file:///") || checkIfWeHaveLocally(url)
    }

    private fun createNewFragment(url: String): Boolean {
        return when {
            url.startsWith("file:///") && url.contains("section") && url.endsWith(".html") -> {
                callBack.onLinkClicked(url.split("/").last())
                true
            }
            url.startsWith("file:///") && url.contains("art") && url.endsWith(".html") -> {
                callBack.onLinkClicked(url.split("/").last())
                true
            }
            else -> false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun shouldInterceptRequest(webView: WebView?, url: String?): WebResourceResponse? {
        return shouldIntercept(webView, url)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun shouldInterceptRequest(
        webView: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        return shouldIntercept(webView, request?.url?.toString())
    }

    private fun shouldIntercept(webView: WebView?, url: String?): WebResourceResponse? {
        if (url == null) {
            log.info("Abort intercepting a request with a null url")
            return null
        }

        if (webView == null) {
            log.info("Abort intercepting a request for url=$url with a a null webView")
            return null
        }

        return runBlocking {
            val decodedUrl = URLDecoder.decode(url, "UTF-8")
            if (handleLinks(decodedUrl)) {
                createCustomWebResourceResponse(decodedUrl)
            } else {
                null
            }
        }
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
    private suspend fun overrideInternalLinks(url: String): String? {
        val fileName = url.substring(url.lastIndexOf('/') + 1, url.length)
        val fileEntry = fileEntryRepository.get(fileName)
        return fileEntry?.let { return storageService.getFileUri(it) }
    }

    /**
     * handle correctly different resource types
     * TODO not sure whether these are all possible resource types and whether all mimeTypes are correct
     */
    private suspend fun createCustomWebResourceResponse(url: String): WebResourceResponse? {
        val internalUrl = overrideInternalLinks(url)
        if (internalUrl == null) {
            log.info("Could not create internal url for url=$url")
            return null
        }

        val data = File(internalUrl.removePrefix("file://"))
        val inputStream = try {
            data.inputStream()
        } catch (e: Exception) {
            val hint = "trying to open non-existent file $url (internal: $internalUrl)"
            log.error(hint)
            Sentry.captureException(e, hint)
            return null
        }

        return when {
            url.endsWith(".css") ->
                WebResourceResponse("text/css", "UTF-8", inputStream)
            url.endsWith(".html") ->
                WebResourceResponse("text/html", "UTF-8", inputStream)
            url.endsWith(".js") ->
                WebResourceResponse("application/javascript", "UTF-8", inputStream)
            url.endsWith(".png") ->
                WebResourceResponse("image/png", "binary", inputStream)
            url.endsWith(".svg") ->
                WebResourceResponse("image/svg+xml", "UTF-8", inputStream)
            url.endsWith(".woff") ->
                WebResourceResponse("font/woff", "binary", inputStream)
            else ->
                WebResourceResponse("text/plain", "UTF-8", inputStream)
        }
    }

    private suspend fun checkIfWeHaveLocally(url: String): Boolean {
        val fileName = url.substring(url.lastIndexOf('/') + 1, url.length)
        return (fileEntryRepository.get(fileName) != null)
    }

    override fun onPageFinished(webview: WebView, url: String) {
        super.onPageFinished(webview, url)
        callBack.onPageFinishedLoading()
    }
}
