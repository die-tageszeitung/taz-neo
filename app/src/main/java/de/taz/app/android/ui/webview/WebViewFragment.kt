package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.view.*
import android.webkit.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import de.taz.app.android.R
import java.io.File
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


abstract class WebViewFragment : Fragment(), AppWebViewCallback {

    private val log by Log
    private lateinit var webView : AppWebView
    val fileLiveData = MutableLiveData<File?>().apply { value = null }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onResume() {
        super.onResume()

        webView = requireActivity().findViewById(R.id.web_view)
        webView.webViewClient = AppWebViewClient(this)
        webView.webChromeClient = WebChromeClient()
        webView.settings.javaScriptEnabled = true
        webView.setArticleWebViewCallback(this)

        fileLiveData.observe(this@WebViewFragment, Observer { file ->
            file?.let {
                context?.let {
                    webView.addJavascriptInterface(TazApiJs(), "ANDROIDAPI")
                    CoroutineScope(Dispatchers.IO).launch {
                        activity?.runOnUiThread { webView.loadUrl("file://${file.absolutePath}") }
                    }
                }
            }
        })

        // handle clicks of the back button
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
            webView.loadUrl("javascript:$call")
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
        log.debug("${webView.scrollX}, ${webView.scrollY}")
    }

    override fun onScrollFinished() {
        log.debug("${webView.scrollX}, ${webView.scrollY}")
    }

}
