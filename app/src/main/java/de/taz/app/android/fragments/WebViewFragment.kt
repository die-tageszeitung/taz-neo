package de.taz.app.android.fragments

import android.content.Context
import android.os.*
import android.view.*
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

        webView.setOnKeyListener(object: View.OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == MotionEvent.ACTION_UP && webView.canGoBack()) {
                    webView.goBack()
                    return true
                }
                return false
            }
        })
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

        val data = File(newUrl.toString().removePrefix("file:///"))

        return when {
            url.toString().contains(".css") -> WebResourceResponse("text/css", "UTF-8", data.inputStream())
            url.toString().contains(".html") -> WebResourceResponse("text/html", "UTF-8", data.inputStream())
            url.toString().contains(".js") -> WebResourceResponse("application/javascript", "UTF-8", data.inputStream())
            url.toString().contains(".png") -> WebResourceResponse("image/png", "binary", data.inputStream())
            url.toString().contains(".svg") -> WebResourceResponse("image/svg+xml", "UTF-8", data.inputStream())
            url.toString().contains(".woff") -> WebResourceResponse("font/woff", "binary", data.inputStream())
            else -> WebResourceResponse("text/plain", "UTF-8", data.inputStream())
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        request?.let { request ->
            val newUrl = overrideInternalLinks(view, request.url.toString())
            val data = File(newUrl.toString().removePrefix("file:///"))

            // handle correctly different resource types
            // we have to return our own WebResourceResponse object here
            // TODO not sure whether these are all possible resource types and whether all mimeTypes are correct
            return when {
                newUrl.toString().contains(".css") -> WebResourceResponse("text/css", "UTF-8", data.inputStream())
                newUrl.toString().contains(".html") -> WebResourceResponse("text/html", "UTF-8", data.inputStream())
                newUrl.toString().contains(".js") -> WebResourceResponse("application/javascript", "UTF-8", data.inputStream())
                newUrl.toString().contains(".png") -> WebResourceResponse("image/png", "binary", data.inputStream())
                newUrl.toString().contains(".svg") -> WebResourceResponse("image/svg+xml", "UTF-8", data.inputStream())
                newUrl.toString().contains(".woff") -> WebResourceResponse("font/woff", "binary", data.inputStream())
                else -> WebResourceResponse("text/plain", "UTF-8", data.inputStream())
            }
        }
        return null
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