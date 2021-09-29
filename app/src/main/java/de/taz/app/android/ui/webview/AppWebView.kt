package de.taz.app.android.ui.webview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import de.taz.app.android.singletons.TazApiCssHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder


const val MAILTO_PREFIX = "mailto:"

class AppWebView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyle: Int = 0
) : WebView(context, attributeSet, defStyle) {

    private val log by Log

    init {
        // prevent horizontal scrolling
        isHorizontalScrollBarEnabled = false
        setOnTouchListener(object : OnTouchListener {
            var x = 0f
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                if (event.pointerCount > 1) { //Multi touch detected
                    return true
                }
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // save x
                        x = event.x
                    }
                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                        // set x so that it doesn't move
                        event.setLocation(x, event.y)
                    }
                }
                return false
            }
        })
        settings.allowFileAccess = true
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

}
