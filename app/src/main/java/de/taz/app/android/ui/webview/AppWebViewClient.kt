package de.taz.app.android.ui.webview

import android.content.Intent
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import de.taz.app.android.R
import de.taz.app.android.util.ToastHelper
import java.io.File
import android.net.Uri
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import java.net.URLDecoder

const val MAILTO_PREFIX = "mailto:"

class AppWebViewClient(private val presenter: WebViewPresenter) : WebViewClient() {

    private val log by Log
    private val fileHelper = FileHelper.getInstance()
    private val toastHelper = ToastHelper.getInstance()

    @SuppressWarnings("deprecation")
    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(webView: WebView?, url: String?): Boolean {
        val handled = if (handleLinks(webView, url)) {
            createNewFragment(url)
        } else {
            handleExternally(webView , url)
        }
        return handled || super.shouldOverrideUrlLoading(webView, url)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(webView: WebView?, request: WebResourceRequest?): Boolean {
        val url = URLDecoder.decode(request?.url.toString(), "UTF-8")
        val handled = if (handleLinks(webView, url)) {
            createNewFragment(url)
        } else {
            handleExternally(webView, url)
        }
        return handled || super.shouldOverrideUrlLoading(webView, request)
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
            if(handleLinks(webView, url)) {
                return createCustomWebResourceResponse(webView, url)
            }
        }
        return null
    }

    private fun handleExternally(webView: WebView?, url: String?): Boolean {
        webView?.let {
            url?.let {
                log.debug("handling $url externally")
                if (url.startsWith(MAILTO_PREFIX)) {
                    sendMail(webView, url)
                } else {
                    openInBrowser(webView, url)
                }
            }
        }
        return true
    }

    private fun sendMail(webView: WebView, url: String) {
        val mail = url.replaceFirst(MAILTO_PREFIX, "")
        log.debug("sending mail to $url")
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse(MAILTO_PREFIX)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(mail))
        }
        try {
            webView.context?.startActivity(intent)
        }
        catch (e: Exception) {
            log.warn("Sending email failed", e)
            toastHelper.makeToast(R.string.toast_no_email_client)
        }
    }

    private fun openInBrowser(webView: WebView, url: String) {
        webView.context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
    private fun createCustomWebResourceResponse(view: WebView?, url: String?): WebResourceResponse {
        val newUrl = overrideInternalLinks(view, url)
        val data = File(newUrl.toString().removePrefix("file:///"))
        log.debug("Intercepted Url is $url")

        return when {
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
    }

    override fun onPageFinished(webview: WebView, url: String) {
        super.onPageFinished(webview, url)

        presenter.onPageFinishedLoading()
    }
}
