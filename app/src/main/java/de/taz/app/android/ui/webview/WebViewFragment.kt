package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.os.*
import android.view.*
import android.webkit.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.util.FileHelper
import kotlinx.android.synthetic.main.fragment_webview.*
import java.io.File
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


open class WebViewFragment : Fragment(), ArticleWebViewCallback {

    lateinit var file: File
    private val log by Log

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onResume() {
        super.onResume()
        web_view.webViewClient = TazWebViewClient(this)
        web_view.webChromeClient = WebChromeClient()
        web_view.settings.javaScriptEnabled = true
        web_view.setArticleWebViewCallback(this)

        context?.let {
            web_view.addJavascriptInterface(TazApiJs(), "ANDROIDAPI")
            CoroutineScope(Dispatchers.IO).launch {
                activity?.runOnUiThread { web_view.loadUrl("file://${file.absolutePath}") }
            }
        }


        // handle clicks of the back button
        web_view.setOnKeyListener(object: View.OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == MotionEvent.ACTION_UP && web_view.canGoBack()) {
                    web_view.goBack()
                    return true
                }
                return false
            }
        })
    }

    private fun callTazApi(methodname: String, vararg params: Any) {

        val jsBuilder = StringBuilder()
        jsBuilder.append("tazApi")
            .append(".")
            .append(methodname)
            .append("(")
        for (i in params.indices) {
            val param = params[i]
            if (param is String) {
                jsBuilder.append("'")
                jsBuilder.append(param)
                jsBuilder.append("'")
            } else
                jsBuilder.append(param)
            if (i < params.size - 1) {
                jsBuilder.append(",")
            }
        }
        jsBuilder.append(");")
        val call = jsBuilder.toString()
        CoroutineScope(Dispatchers.Main).launch{
            log.info("Calling javascript with $call")
            web_view.loadUrl("javascript:$call")
        }
    }

    private fun onGestureToTazapi(gesture: GESTURES, e1: MotionEvent) {
        callTazApi("onGesture", gesture.name, e1.x, e1.y)
    }

    override fun onSwipeLeft(e1: MotionEvent, e2: MotionEvent) {
        log.debug("swiping left")
        onGestureToTazapi(GESTURES.swipeLeft, e1)
    }

    override fun onSwipeRight(e1: MotionEvent, e2: MotionEvent) {
        onGestureToTazapi(GESTURES.swipeRight, e1)
    }


    override fun onSwipeTop(e1: MotionEvent, e2: MotionEvent) {
        onGestureToTazapi(GESTURES.swipeUp, e1)
    }

    override fun onSwipeBottom(e1: MotionEvent, e2: MotionEvent) {
        onGestureToTazapi(GESTURES.swipeDown, e1)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
//        if (getReaderActivity() != null) {
//            getReaderActivity().speak(articleViewModel.getKey(), getTextToSpeech());
//        }
        return true
    }

    private enum class GESTURES {
        swipeUp, swipeDown, swipeRight, swipeLeft
    }

    override fun onScrollStarted() {
        log.debug("${web_view?.scrollX}, ${web_view?.scrollY}")
    }

    override fun onScrollFinished() {
        log.debug("${web_view?.scrollX}, ${web_view?.scrollY}")
    }


}

class TazWebViewClient(val fragment: WebViewFragment) : WebViewClient() {

    private val log by Log
    private val fileHelper = FileHelper.getInstance()

    /** internal links should be handled by the app, external ones - by a web browser
        this function checks whether a link is internal
     */
    private fun handleLinks(view: WebView?, url: String?) : Boolean {
        url?.let {urlString ->
            view?.let {
                if (urlString.startsWith(fileHelper.getFileDirectoryUrl(view.context))) {
                    return true
                }
            }
        }
        return false
    }

    private fun createNewFragment(view: WebView?, url: String?): Boolean {
        url?.let{
            when {
                it.startsWith("file:///") && it.contains("section") && it.endsWith(".html")-> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val section = SectionRepository.getInstance().get(
                            url.split("/").last()
                        )
                        section?.let {
                            showFragment(SectionWebViewFragment(section))
                        }
                    }
                }
                it.startsWith("file:///") && it.contains("art") && it.endsWith(".html")-> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val article = ArticleRepository.getInstance().get(
                            url.split("/").last()
                        )
                        article?.let {
                            showFragment(ArticleWebViewFragment(article))
                        }
                    }
                }
                else -> return false
            }
        }
        return false
    }

    @SuppressLint("Deprecated")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (handleLinks(view, url)) {
            createNewFragment(view, url)
        }
        return super.shouldOverrideUrlLoading(view, url)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (handleLinks(view, request?.url.toString())){
            val url = request?.url.toString()
            createNewFragment(view, url)
        }
        return super.shouldOverrideUrlLoading(view, request)
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
    private fun createCustomWebResourceResponse (url: String?, data: File) : WebResourceResponse {
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

    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        val newUrl = overrideInternalLinks(view, url)
        val data = File(newUrl.toString().removePrefix("file:///"))
        log.debug("Intercepted Url is ${url.toString()}")

        return createCustomWebResourceResponse(newUrl, data)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        request?.let {
            val newUrl = overrideInternalLinks(view, request.url.toString())
            val data = File(newUrl.toString().removePrefix("file:///"))
            log.debug("Intercepted Url is ${request.url}")

            return createCustomWebResourceResponse(newUrl, data)
        }
        return null
     }

    private fun showFragment(fragment: WebViewFragment) {
        fragment.activity?.apply{
            runOnUiThread {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.main_content_fragment_placeholder,
                        fragment
                    )
                    .commit()
                drawer_layout.closeDrawer(GravityCompat.START)
            }
        }
    }
}
