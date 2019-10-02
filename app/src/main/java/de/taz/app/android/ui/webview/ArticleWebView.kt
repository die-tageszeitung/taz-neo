package de.taz.app.android.ui.webview

import android.view.MotionEvent
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.GestureDetector
import android.view.animation.AccelerateDecelerateInterpolator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView
import de.taz.app.android.util.Log


class ArticleWebView : WebView {

    private val log by Log
    private lateinit var mContext: Context

    internal var isScroll: Boolean = false

    internal var mScrolling: Boolean = false

    internal var scrollCheckDelay = 100

    internal var isAlreadyChecking = false
    internal var lastCheckedY = 0
    internal var lastCheckedX = 0
    internal var checkY = 0
    internal var checkX = 0
    internal var scrollStopCheckerTask: Runnable = object : Runnable {

        override fun run() {
            if (checkY != lastCheckedY || checkX != lastCheckedX) {
                lastCheckedX = checkX
                lastCheckedY = checkY
                this@ArticleWebView.postDelayed(this, scrollCheckDelay.toLong())
            } else {
                mScrolling = false
                if (mCallback != null) {
                    mCallback!!.onScrollFinished(this@ArticleWebView)
                }
                isAlreadyChecking = false
            }

        }
    }

    val contentWidth: Int
        get() = computeHorizontalScrollRange()


    private var gestureDetector: GestureDetector? = null

    private var mCallback: ArticleWebViewCallback? = null

    internal var simpleOnGestureListener: SimpleOnGestureListener =
        object : SimpleOnGestureListener() {

            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onDoubleTap(e: MotionEvent): Boolean {
                return if (mCallback != null) mCallback!!.onDoubleTap(e) else super.onDoubleTap(e)
            }

            override fun onFling(
                e1: MotionEvent,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                log.debug("e1: $e1, e2: $e2, velocityX: $velocityX, velocityY: $velocityY")
                var result = false

                try {
                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) {
                                if (mCallback != null) mCallback!!.onSwipeRight(
                                    this@ArticleWebView,
                                    e1,
                                    e2
                                )
                            } else {
                                if (mCallback != null) mCallback!!.onSwipeLeft(
                                    this@ArticleWebView,
                                    e1,
                                    e2
                                )
                            }
                        }
                        result = true
                    } else if (!isScroll && Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            if (mCallback != null) mCallback!!.onSwipeBottom(
                                this@ArticleWebView,
                                e1,
                                e2
                            )
                        } else {
                            if (mCallback != null) mCallback!!.onSwipeTop(
                                this@ArticleWebView,
                                e1,
                                e2
                            )
                        }
                        result = true
                    }

                } catch (exception: Exception) {
                    exception.printStackTrace()
                }

                return result
            }
        }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context) : super(context) {
        init(context)
    }

    @SuppressLint("NewApi")
    private fun init(context: Context) {
        mContext = context
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun setIsScroll() {
//        isScroll = TazSettings.getInstance(mContext)
//            .getPrefBoolean(TazSettings.PREFKEY.ISSCROLL, false)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        log.debug("l: $l, t: $t, oldl: $oldl, oldt: $oldt")
        checkY = t
        checkX = l
        super.onScrollChanged(l, t, oldl, oldt)
        if (!isAlreadyChecking) {
            mScrolling = true
            mCallback!!.onScrollStarted(this)
            isAlreadyChecking = true
            this.postDelayed(scrollStopCheckerTask, scrollCheckDelay.toLong())
        }
    }

    fun smoothScrollToY(y: Int) {
        val density = resources.displayMetrics.density
        val scrollAnimation = ObjectAnimator.ofInt(this, "scrollY", Math.round(y * density))
        scrollAnimation.duration = 500
        scrollAnimation.interpolator = AccelerateDecelerateInterpolator()
        scrollAnimation.start()
    }

    override fun loadUrl(url: String) {
        log.info("url: $url")
        gestureDetector = GestureDetector(mContext, simpleOnGestureListener)
        setIsScroll()
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
        gestureDetector = GestureDetector(mContext, simpleOnGestureListener)
        setIsScroll()
        super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, failUrl)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return if (!mScrolling && gestureDetector != null) {
            gestureDetector!!.onTouchEvent(ev) || super.onTouchEvent(ev)
        } else
            super.onTouchEvent(ev)

    }

    fun setArticleWebViewCallback(listener: ArticleWebViewCallback) {
        mCallback = listener
    }

    interface ArticleWebViewCallback {

        fun onScrollStarted(view: ArticleWebView)

        fun onScrollFinished(view: ArticleWebView)

        fun onSwipeRight(view: ArticleWebView, e1: MotionEvent, e2: MotionEvent)

        fun onSwipeLeft(view: ArticleWebView, e1: MotionEvent, e2: MotionEvent)

        fun onSwipeBottom(view: ArticleWebView, e1: MotionEvent, e2: MotionEvent)

        fun onSwipeTop(view: ArticleWebView, e1: MotionEvent, e2: MotionEvent)

        fun onDoubleTap(e: MotionEvent): Boolean
    }
}