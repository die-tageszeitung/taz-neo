package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File


class AppWebViewClient(private val fragment: WebViewFragment) : WebViewClient() {

    private val log by Log
    private val fileHelper = FileHelper.getInstance()

    @SuppressLint("Deprecated")
    override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, url: String?): Boolean {
        if (handleLinks(view, url)) {
            createNewFragment(url)
        }
        return super.shouldOverrideUrlLoading(view, url)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: WebResourceRequest?): Boolean {
        if (handleLinks(view, request?.url.toString())){
            val url = request?.url.toString()
            createNewFragment(url)
        }
        return super.shouldOverrideUrlLoading(view, request)
    }

    /** internal links should be handled by the app, external ones - by a web browser
    this function checks whether a link is internal
     */
    private fun handleLinks(view: android.webkit.WebView?, url: String?) : Boolean {
        url?.let {urlString ->
            view?.let {
                if (urlString.startsWith(fileHelper.getFileDirectoryUrl(view.context))) {
                    return true
                }
            }
        }
        return false
    }

    private fun createNewFragment(url: String?): Boolean {
        url?.let{
            when {
                it.startsWith("file:///") && it.contains("section") && it.endsWith(".html")-> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val section = SectionRepository.getInstance().get(
                            url.split("/").last()
                        )
                        section?.let {
                            fragment.lifecycleScope.launch {
                                showFragment(SectionWebViewFragment(section))
                            }
                        }
                    }
                }
                it.startsWith("file:///") && it.contains("art") && it.endsWith(".html")-> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val article = ArticleRepository.getInstance().get(
                            url.split("/").last()
                        )
                        article?.let {
                            fragment.lifecycleScope.launch {
                                showFragment(ArticleWebViewFragment(article))
                            }
                        }
                    }
                }
                else -> return false
            }
        }
        return false
    }

    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        return createCustomWebResourceResponse(view, url)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        request?.let {
            return createCustomWebResourceResponse(view, request.url.toString())
        }
        return null
    }

    /**
     * intercept links to "resources/" and "global/" and point them to the correct directories
     */
    private fun overrideInternalLinks(view: WebView?, url: String?) : String? {
        view?.let {
            url?.let {
                val fileDir = fileHelper.getFileDirectoryUrl(view.context)

                var newUrl = url.replace("$fileDir/\\w+/\\d{4}-\\d{2}-\\d{2}/resources/".toRegex(), "$fileDir/resources/")
                newUrl = newUrl.replace("$fileDir/\\w+/\\d{4}-\\d{2}-\\d{2}/global/".toRegex(), "$fileDir/global/")

                return newUrl
            }

        }
        return url
    }

    /**
     * handle correctly different resource types
     * TODO not sure whether these are all possible resource types and whether all mimeTypes are correct
     */
    private fun createCustomWebResourceResponse (view: WebView?, url: String?) : WebResourceResponse {
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

    private fun showFragment(newFragment: WebViewFragment) {
        fragment.activity?.apply{
            runOnUiThread {
                supportFragmentManager
                    .beginTransaction()
                    .replace(
                        R.id.main_content_fragment_placeholder,
                        newFragment
                    )
                    .commit()
                drawer_layout.closeDrawer(GravityCompat.START)
            }
        }
    }
}
