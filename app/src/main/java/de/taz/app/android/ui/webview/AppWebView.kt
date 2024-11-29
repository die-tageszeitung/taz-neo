package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.Base64
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.webkit.ValueCallback
import androidx.annotation.UiThread
import de.taz.app.android.R
import de.taz.app.android.singletons.TazApiCssHelper
import de.taz.app.android.ui.ViewBorder
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import kotlin.math.abs


private const val MAILTO_PREFIX = "mailto:"

class AppWebView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : NestedScrollWebView(context, attributeSet) {

    init {
        isHorizontalScrollBarEnabled = false
        settings.allowFileAccess = true
    }

    private val log by Log

    var touchDisabled = false

    /**
     * returns true if the event was consumed - otherwise false
     */
    var onBorderTapListener: ((ViewBorder) -> Boolean)? = null

    override fun loadUrl(url: String) {
        log.debug("loading url: $url")
        val decodedUrl = URLDecoder.decode(url, "UTF-8")
        if (decodedUrl.startsWith(MAILTO_PREFIX)) {
            sendMail(decodedUrl)
            return
        }

        super.loadUrl(decodedUrl)
    }


    private fun sendMail(url: String) {
        val mail = url.replaceFirst(MAILTO_PREFIX, "")
        log.debug("sending mail to $url")
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse(MAILTO_PREFIX)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(mail))
        }
        context?.startActivity(intent)
    }

    /**
     * This method will help to re-inject css into the WebView upon changes
     * to the corresponding shared preferences
     */
    suspend fun injectCss() = withContext(Dispatchers.Main) {
        val cssString = TazApiCssHelper.getInstance(context.applicationContext).generateCssString()
        val encoded = Base64.encodeToString(cssString.toByteArray(), Base64.NO_WRAP)
        callTazApi("injectCss", encoded)
    }

    @UiThread
    fun callTazApi(functionName: String, vararg arguments: Any, callback: ValueCallback<String>? = null) {
        val argumentsString = arguments
            .map { argument ->
                when (argument) {
                    is Number -> argument.toString()
                    is Boolean -> if (argument) "true" else "false"
                    is String -> "\"$argument\""
                    else -> throw IllegalArgumentException("Only string, numbers and booleans are supported for tazApiJs calls")
                }
            }
            .joinToString(",")
        evaluateJavascript("(function(){ return tazApi.$functionName($argumentsString);})()", callback)
    }

    interface WebViewScrollListener{
        fun onScroll(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int)
    }

    interface WebViewOverScrollListener {
        fun onOverScroll(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean)
    }

    var scrollListener: WebViewScrollListener? = null
    var overScrollListener: WebViewOverScrollListener? = null

    override fun onScrollChanged(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
        super.onScrollChanged(scrollX, scrollY, oldScrollX, oldScrollY)
        scrollListener?.onScroll(scrollX, scrollY, oldScrollX, oldScrollY)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        overScrollListener?.onOverScroll(scrollX, scrollY, clampedX, clampedY)
    }

    var pagingEnabled: Boolean = false
    var showTapIconsListener: ((Boolean) -> Unit)? = null

    private val minVerticalScrollDetectDistancePx =
        resources.getDimensionPixelSize(R.dimen.webview_multicolumn_pager_min_vertical_scroll_detect_distance)

    private val gestureDetector: GestureDetector

    private var onTouchListener: OnTouchListener? = null

    fun addOnTouchListener(touchListener: OnTouchListener?) {
        onTouchListener = touchListener
    }

    fun clearOnTouchListener() {
        onTouchListener = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (touchDisabled) return true

        var consumed = false
        if (onTouchListener != null)
            consumed = onTouchListener!!.onTouch(this, ev)
        if (ev != null && ev.pointerCount == 1) {
            gestureDetector.onTouchEvent(ev)
        }
        return consumed || super.onTouchEvent(ev)
    }

    private val gestureListener = object : SimpleOnGestureListener() {
        // call onBordertapListener if clicks on the border
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            return handleTap(e.x) || super.onSingleTapUp(e)
        }

        private fun handleTap(x: Float): Boolean {
            val tapBarWidth =
                resources.getDimension(R.dimen.tap_bar_width)
            val consumed = if (width > 0 && x < tapBarWidth) {
                onBorderTapListener?.invoke(ViewBorder.LEFT)
            } else if (width > 0 && x > width - tapBarWidth) {
                onBorderTapListener?.invoke(ViewBorder.RIGHT)
            } else {
                onBorderTapListener?.invoke(ViewBorder.NONE)
            }
            return consumed ?: false
        }

        // call showTapIconsListener if vertical scroll
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (e1 != null) {
                val horizontalScrollDistance = abs(e2.x - e1.x)
                val verticalScrollDistance = abs(e2.y - e1.y)

                val verticalScrollDetected =
                    verticalScrollDistance > minVerticalScrollDetectDistancePx && verticalScrollDistance > 3 * horizontalScrollDistance
                showTapIconsListener?.invoke(verticalScrollDetected)
            }
            return super.onScroll(e1, e2, distanceX, distanceY)
        }
    }


    init {
        gestureDetector = GestureDetector(context, gestureListener)
    }
}