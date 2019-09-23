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
import kotlinx.android.synthetic.main.fragment_webview.*

class WebViewFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView.webViewClient = TazWebViewClient()
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

    private fun overrideInternalLinks(view: WebView?, url: String?) : String {
        view?.let {
            url?.let {
                val fileDir = FileUtil.getFileDirectoryUrl(view.context)

                if (url.matches(""$fileDir")) {
                    return "$fileDir/"
                }
                if (url.startsWith("$fileDir/global/"))
            }

        }
    }

    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        return super.shouldInterceptRequest(view, url)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        return super.shouldInterceptRequest(view, request)
    }
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