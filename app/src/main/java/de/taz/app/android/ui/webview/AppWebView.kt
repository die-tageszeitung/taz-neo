package de.taz.app.android.ui.webview

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
import java.util.Calendar

private const val MAILTO_PREFIX = "mailto:"
private const val MAX_DOWN_DURATION = 200L

class AppWebView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyle: Int = 0
) : WebView(context, attributeSet, defStyle) {
    var onBorderTapListener: ((ViewBorder) -> Unit)? = null
    private val log by Log

    init {
        // prevent horizontal scrolling
        isHorizontalScrollBarEnabled = false
        settings.allowFileAccess = true
    }

    private var initialTouchX = 0f
    private var initialTouchDownTimeMs = 0L
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // If a touchListener is currently set, it will fully override the default touch handling.
        // For null safety reasons of the Kotlin type system we have to store an intermediate reference to the current touch listener.
        val currentTouchListener = overrideTouchListener
        if (currentTouchListener != null) {
            return currentTouchListener.onTouch(this, event)
        }

        if (event.pointerCount > 1) {
            // We do not want to handle multi touch events to be handled:
            // Ignore the event but signal that we have consumed it
            return true
        }

        if (event.action == MotionEvent.ACTION_DOWN) {
            // Save the initial touch x position and re-set it on the following events,
            // to prevent the webview from being able to be scrolled horizontally if its content
            // is larger then the webview itself
            initialTouchX = event.x
        } else {
            event.setLocation(initialTouchX, event.y)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchDownTimeMs = Calendar.getInstance().timeInMillis
            }
            MotionEvent.ACTION_UP -> {
                val duration =  Calendar.getInstance().timeInMillis - initialTouchDownTimeMs
                if (duration < MAX_DOWN_DURATION) {
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
