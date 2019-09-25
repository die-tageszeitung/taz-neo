package de.taz.app.android.fragments

import android.content.Context
import android.net.Uri
import android.os.BadParcelableException
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.api.models.Issue
import kotlinx.android.synthetic.main.fragment_webview.*
import java.io.File

class WebViewFragment(val lastIssue: Issue) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview, container, false)
    }

    override fun onResume() {
        super.onResume()
        webView.webViewClient = TazWebViewClient()
        context?.let {
            val file = File(
                ContextCompat.getExternalFilesDirs(it.applicationContext, null).first(),
                "${lastIssue.tag}/${lastIssue.sectionList.first().sectionHtml.name}"
            )
            webView.loadUrl("file://${file.absolutePath}")
        }
    }

}

class TazWebViewClient : WebViewClient() {

    private fun handleInternalLinks(view: WebView?, url: String?) : Boolean {
        url?.let {urlString ->
            view?.let {
                if (urlString.startsWith(FileUtil.getFileDirectoryUrl(view.context))) {
                    return true
                }
            }
        }
        return false
    }

    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (handleInternalLinks(view, url)) return false
        return super.shouldOverrideUrlLoading(view, url)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (handleInternalLinks(view, request?.url.toString())) return false
        return super.shouldOverrideUrlLoading(view, request)
    }

    private fun overrideInternalLinks(view: WebView?, url: String?) : String? {
        view?.let {
            url?.let {
                val fileDir = FileUtil.getFileDirectoryUrl(view.context)

                var newUrl = url.replace("$fileDir/\\w+/\\d{4}-\\d{2}-\\d{2}/resources/".toRegex(), "$fileDir/resources/")
                newUrl = newUrl.replace("$fileDir/\\w+/\\d{4}-\\d{2}-\\d{2}/global/".toRegex(), "$fileDir/global/")

                return newUrl
            }

        }
        return url
    }

    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        val newUrl = overrideInternalLinks(view, url)
        log.debug("newUrl is $newUrl")

        val data = File(newUrl.toString().removePrefix("file:///"))
        log.debug("blaaaaaaa")

        return when {
            url.toString().contains( ".css") -> WebResourceResponse("text/css", "UTF-8", data.inputStream())
            url.toString().contains( ".html") -> WebResourceResponse("text/html", "UTF-8", data.inputStream())
            url.toString().contains( ".js") -> WebResourceResponse("application/javascript", "UTF-8", data.inputStream())
            url.toString().contains( ".png") -> WebResourceResponse("image/png", "binary", data.inputStream())
            url.toString().contains( ".svg") -> WebResourceResponse("image/svg+xml", "UTF-8", data.inputStream())
            url.toString().contains( ".woff") -> WebResourceResponse("font/woff", "binary", data.inputStream())
            else -> WebResourceResponse("text/plain", "UTF-8", data.inputStream())
        }
    }

//    @RequiresApi(api = Build.VERSION_CODES.N)
//    override fun shouldInterceptRequest(
//        view: WebView?,
//        request: WebResourceRequest?
//    ): WebResourceResponse? {
//        request?.let { request ->
//            val newUrl = overrideInternalLinks(view, request.url.toString())
//            if (request.url.toString() != newUrl) {
//                    return readFileFromFileSystem(newUrl)
//                }
//        }
//        return super.shouldInterceptRequest(view, request)
//     }
//
//    private fun readFileFromFileSystem(url: String) : WebResourceResponse {
//        File.
//    }
}

object FileUtil {
    fun getFileDirectoryUrl(context: Context, internal: Boolean = false) : String {
        context.applicationContext.let {
            return if (internal)
                "file://${it.filesDir.absolutePath}"
            else
                "file://${ContextCompat.getExternalFilesDirs(it,null).first().absolutePath}"
        }

    }
}