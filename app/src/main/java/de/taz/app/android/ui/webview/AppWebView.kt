package de.taz.app.android.ui.webview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import de.taz.app.android.util.Log
import java.net.URLDecoder


const val MAILTO_PREFIX = "mailto:"

class AppWebView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyle: Int = 0
) : WebView(context, attributeSet, defStyle) {

    private val log by Log

    private var callback: AppWebViewCallback? = null

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
    }

    override fun loadUrl(url: String) {
        log.info("url: $url")
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


    override fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        failUrl: String?
    ) {
        log.info(
            "baseUrl: $baseUrl, mimeType: $mimeType, encoding: $encoding, failUrl: $failUrl"
        )
        log.debug("data: $data")
        super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, failUrl)
    }

    fun setArticleWebViewCallback(listener: AppWebViewCallback) {
        callback = listener
    }

}
