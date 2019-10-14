package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import android.webkit.*
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.MainActivity
import de.taz.app.android.R
import java.io.File
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


abstract class WebViewFragment : Fragment(), AppWebViewCallback {

    private val log by Log

    val fileLiveData = MutableLiveData<File?>().apply { value = null }

    @get:MenuRes
    abstract val menuId: Int
    @get:LayoutRes
    abstract val headerId: Int

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHeader()
        setBottomNavigation()

        configureWebView()

        observeFile()
    }

    private fun setHeader() {
        (header_placeholder as ViewGroup).apply {
            addView(layoutInflater.inflate(headerId, this, false))
        }
    }

    private fun setBottomNavigation() {
        activity?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.apply {
            visibility = View.GONE
            menu.clear()
            inflateMenu(menuId)
            menu.getItem(0).isCheckable = false
            visibility = View.VISIBLE
            setOnNavigationItemSelectedListener { menuItem -> run {
                val oldCheckable = menuItem.isChecked && menuItem.isCheckable
                if(!oldCheckable) {
                    onBottomNavigationItemSelected(menuItem)
                }
                menuItem.isChecked = !oldCheckable
                menuItem.isCheckable = !oldCheckable
                false
            }}

            setOnNavigationItemReselectedListener {  menuItem -> run {
                val oldCheckable = menuItem.isChecked && menuItem.isCheckable
                if(!oldCheckable) {
                    onBottomNavigationItemSelected(menuItem)
                }
                menuItem.isChecked = !oldCheckable
                menuItem.isCheckable = !oldCheckable
            }}
        }
    }

    abstract fun onBottomNavigationItemSelected(menuItem: MenuItem)

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun configureWebView() {
        web_view.webViewClient = AppWebViewClient(this)
        web_view.webChromeClient = WebChromeClient()
        web_view.settings.javaScriptEnabled = true
        web_view.setArticleWebViewCallback(this)
    }

    private fun observeFile() {
        fileLiveData.observe(this@WebViewFragment, Observer { file ->
            file?.let {
                context?.let {
                    web_view.addJavascriptInterface(TazApiJs(activity as MainActivity), "ANDROIDAPI")
                    CoroutineScope(Dispatchers.IO).launch {
                        activity?.runOnUiThread { web_view.loadUrl("file://${file.absolutePath}") }
                    }
                }
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
        CoroutineScope(Dispatchers.Main).launch {
            log.info("Calling javascript with $call")
            web_view.evaluateJavascript(call) { result -> log.debug("javascript result $result") }
        }
    }

    private fun onGestureToTazapi(gesture: GESTURES, e1: MotionEvent) {
        callTazApi("onGesture", gesture.name, e1.x.toInt(), e1.y.toInt())
    }

    override fun onSwipeLeft(e1: MotionEvent, e2: MotionEvent) {
        log.debug("swiping left")
        onGestureToTazapi(GESTURES.swipeLeft, e1)
        // TODO correct implementation
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
