package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.Base64
import android.view.MotionEvent
import android.webkit.WebView
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
private const val TAP_DOWN_DETECT_TIME_MS = 500L
private const val TAP_DISTANCE_TOLERANCE = 50L

class AppWebView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : WebView(context, attributeSet) {

    init {
        isHorizontalScrollBarEnabled = false
        settings.allowFileAccess = true
    }

    private val log by Log

    var onBorderTapListener: ((ViewBorder) -> Unit)? = null

    private var initialX = 0f
    private var initialY = 0f
    private var initialOnLeftBorder = false
    private var initialOnRightBorder = false

    private var overrideTouchListener: OnTouchListener? = null

    /**
     * Override the [AppWebView] touch handling with the given [OnTouchListener].
     * To restore the default behavior pass null or call [clearOnTouchListener].
     * Note: We can't override the setOnTouchListener function directly, because it will also be set
     * from [ArticleWebViewFragment.hideKeyboardOnAllViewsExceptEditText] and will thus disable
     * the tapToScroll functionality.
     */
    fun overrideOnTouchListener(touchListener: OnTouchListener?) {
        overrideTouchListener = touchListener
    }

    /**
     * Clear any custom touch listener and restore the default [AppWebView] touch handling.
     */
    fun clearOnTouchListener() {
        overrideTouchListener = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // If a touchListener is currently set, it will fully override the default touch handling.
        // For null safety reasons of the Kotlin type system we have to store an intermediate reference to the current touch listener.
        val currentTouchListener = overrideTouchListener
        if (currentTouchListener != null) {
            return currentTouchListener.onTouch(this, event)
        }

        if (event.pointerCount > 1) {
            // We do not want to handle multi touch events
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y
                initialOnLeftBorder  = !this.canScrollHorizontally(-1)
                initialOnRightBorder  = !this.canScrollHorizontally(1)
            }
            MotionEvent.ACTION_UP -> {
                val downTime = event.eventTime - event.downTime
                val distanceX = abs(event.x - initialX)
                val distanceY = abs(event.y - initialY)
                if (downTime < TAP_DOWN_DETECT_TIME_MS && distanceX < TAP_DISTANCE_TOLERANCE && distanceY < TAP_DISTANCE_TOLERANCE) {
                    handleTap(event.x)
                }
            }
        }
        return super.onTouchEvent(event)
    }

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
     * Check if the WebView content is scrolled to a horizontal border.
     * In contrast to [WebView.canScrollHorizontally] it will return true,
     * even if the border is only within the [bufferPx] distance.
     *
     * @param direction Negative to check scrolling left, positive to check scrolling right.
     * @param bufferPx Allowed buffer scroll distance to interpret the WebView to be at the border
     *
     * @return true if the WebView is scrolled to the border in the specified direction, false otherwise.
     */
    fun isScrolledToHorizontalBorder(direction: Int, bufferPx: Int): Boolean {
        // Attention: the HorizontalScroll values must not be in Px as per Android docs.
        // but currently they are for WebViews, so we can use the buffer in px to compare them
        val offset = computeHorizontalScrollOffset()
        val range = computeHorizontalScrollRange() - computeHorizontalScrollExtent()

        if (range == 0) {
            return true
        }

        return if (direction < 0) {
            offset - bufferPx < 0
        } else {
            offset + bufferPx >= range
        }
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
    fun callTazApi(functionName: String, vararg arguments: Any) {
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
        evaluateJavascript("(function(){tazApi.$functionName($argumentsString);})()", null)
    }

    private fun handleTap(x: Float) {
        val tapBarWidth =
            resources.getDimension(R.dimen.tap_bar_width)
        if (width > 0 && x < tapBarWidth) {
            onBorderTapListener?.invoke(ViewBorder.LEFT)
        } else if (width > 0 && x > width - tapBarWidth) {
            onBorderTapListener?.invoke(ViewBorder.RIGHT)
        } else {
            onBorderTapListener?.invoke(ViewBorder.NONE)
        }
    }
}