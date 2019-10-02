package de.taz.app.android.ui.webview

import android.view.MotionEvent
import android.view.GestureDetector
import android.view.animation.AccelerateDecelerateInterpolator
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView
import de.taz.app.android.util.Log
import kotlin.math.round


class ArticleWebView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyle: Int = 0
) : WebView(context, attributeSet, defStyle) {

    private val log by Log

    private var isScrolling: Boolean = false

    private var scrollCheckDelay = 100

    private var isAlreadyChecking = false
    private var lastCheckedY = 0
    private var lastCheckedX = 0
    private var checkY = 0
    private var checkX = 0
    private var scrollStopCheckerTask: Runnable = object : Runnable {

        override fun run() {
            if (checkY != lastCheckedY || checkX != lastCheckedX) {
                lastCheckedX = checkX
                lastCheckedY = checkY
                this@ArticleWebView.postDelayed(this, scrollCheckDelay.toLong())
            } else {
                isScrolling = false
                callback?.onScrollFinished()
                isAlreadyChecking = false
            }

        }
    }

    private var callback: ArticleWebViewCallback? = null
    private var gestureDetector: GestureDetector? = null


    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        log.debug("l: $l, t: $t, oldl: $oldl, oldt: $oldt")
        checkY = t
        checkX = l
        super.onScrollChanged(l, t, oldl, oldt)
        if (!isAlreadyChecking) {
            isScrolling = true
            callback!!.onScrollStarted()
            isAlreadyChecking = true
            this.postDelayed(scrollStopCheckerTask, scrollCheckDelay.toLong())
        }
    }

    fun smoothScrollToY(y: Int) {
        val density = resources.displayMetrics.density
        val scrollAnimation = ObjectAnimator.ofInt(this, "scrollY", round(y * density).toInt())
        scrollAnimation.duration = 500
        scrollAnimation.interpolator = AccelerateDecelerateInterpolator()
        scrollAnimation.start()
    }

    override fun loadUrl(url: String) {
        log.info("url: $url")
        super.loadUrl(url)
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

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector?.let {
            if (!isScrolling)
                return it.onTouchEvent(ev) || super.onTouchEvent(ev)
        }
        return super.onTouchEvent(ev)

    }

    fun setArticleWebViewCallback(listener: ArticleWebViewCallback) {
        callback = listener
        gestureDetector = GestureDetector(context, WebViewGestureListener(listener))
    }

}
