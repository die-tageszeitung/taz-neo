package de.taz.app.android.ui.webview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.Base64
import android.view.MotionEvent
import android.webkit.WebView
import de.taz.app.android.singletons.TazApiCssHelper
import de.taz.app.android.ui.ViewBorder
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.*

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
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
        log.debug("Injecting css")

        val cssString = TazApiCssHelper.getInstance(context.applicationContext).generateCssString()
        val encoded = Base64.encodeToString(cssString.toByteArray(), Base64.NO_WRAP)
        log.debug("Injected css: $cssString")
        evaluateJavascript("(function() {tazApi.injectCss(\"$encoded\");})()", null)
    }

    override fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        failUrl: String?
    ) {
        log.debug(
            "loadDataWithBaseURL: baseUrl: $baseUrl, mimeType: $mimeType, encoding: $encoding, failUrl: $failUrl\n data: $data"
        )
        super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, failUrl)
    }

    private fun handleTap(x: Float) {
        log.debug("tapped on x $x")
        if (x<width*0.25 && width >0) {
            onBorderTapListener?.invoke(ViewBorder.LEFT)
        } else if (x>width*0.75 && width >0) {
            onBorderTapListener?.invoke(ViewBorder.RIGHT)
        } else {
            onBorderTapListener?.invoke(ViewBorder.NONE)
        }
    }

}
