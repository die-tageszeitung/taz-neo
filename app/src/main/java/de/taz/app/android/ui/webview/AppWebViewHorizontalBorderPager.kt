package de.taz.app.android.ui.webview

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GestureDetectorCompat
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.sign

class AppWebViewHorizontalBorderPager @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var pagingEnabled: Boolean = false
    var showTapIconsListener: ((Boolean) -> Unit)? = null

    private val minScrollDistancePx =
        resources.getDimensionPixelSize(R.dimen.webview_multicolumn_pager_min_scroll_distance)
    private val borderBufferPx =
        resources.getDimensionPixelSize(R.dimen.webview_multicolumn_pager_border_buffer)
    private val minFlingVelocity =
        ResourcesCompat.getFloat(resources, R.dimen.webview_multicolumn_pager_min_fling_velocity)
    private val minVerticalScrollDetectDistancePx =
        resources.getDimensionPixelSize(R.dimen.webview_multicolumn_pager_min_vertical_scroll_detect_distance)

    private lateinit var webView: AppWebView
    private val gestureDetector: GestureDetectorCompat

    private var initialX: Float = 0f
    private var initialY: Float = 0f

    // Only scroll to another page if the WebView could not scroll into that direction when the Motion started
    private var initialScrolledToLeftBorder: Boolean = false
    private var initialScrolledToRightBorder: Boolean = false

    private var didFlingToPage: Boolean = false

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        check(child is AppWebView && !this::webView.isInitialized) { "The one and only child of a ${this::class.simpleName} must be a WebView" }
        webView = child
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return if (pagingEnabled) {
            onInterceptTouchEventInternal(event)
        } else {
            super.onInterceptTouchEvent(event)
        }
    }

    private fun onInterceptTouchEventInternal(event: MotionEvent): Boolean {
        // Try to detect fling gestures and navigate to another page if the velocity is strong enough
        gestureDetector.onTouchEvent(event)

        var didScrollToPage = false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y
                didFlingToPage = false
                initialScrolledToLeftBorder =
                    webView.isScrolledToHorizontalBorder(-1, borderBufferPx)
                initialScrolledToRightBorder =
                    webView.isScrolledToHorizontalBorder(1, borderBufferPx)

                // Initially on every new gesture, the tap icons will be hidden
                showTapIconsListener?.invoke(false)
            }

            MotionEvent.ACTION_MOVE -> {
                val horizontalScrollDistance = abs(event.x - initialX)
                val verticalScrollDistance = abs(event.y - initialY)

                val verticalScrollDetected = verticalScrollDistance > minVerticalScrollDetectDistancePx && verticalScrollDistance > 3 * horizontalScrollDistance
                showTapIconsListener?.invoke(verticalScrollDetected)
            }

            MotionEvent.ACTION_UP -> {
                // If the user tried to scroll a long distance in the border direction, we navigate
                // to another page, if this motion wasn't already handled by the fling gesture
                if (!didFlingToPage) {
                    val dx = event.x - initialX
                    val dy = event.y - initialY
                    val direction = -(dx.sign.toInt())

                    if (dx.absoluteValue > dy.absoluteValue && dx.absoluteValue > minScrollDistancePx && getInitialScrolledToBorder(
                            direction
                        )
                    ) {
                        scrollToNextItem(direction)
                        didScrollToPage = true
                    }
                }
            }
        }

        // Even if this only happens on the end of an action, we intercept this final event
        return didScrollToPage || didFlingToPage || super.onInterceptTouchEvent(event)
    }

    private val flingGestureListener = object : SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
        ): Boolean {
            val absVelocityX = abs(velocityX)
            val absVelocityY = abs(velocityY)

            // Ignore vertical flings
            if (absVelocityY > absVelocityX) {
                return false
            }

            val direction: Int = -(velocityX.sign.toInt())
            if (absVelocityX > minFlingVelocity && getInitialScrolledToBorder(direction)) {
                scrollToNextItem(direction)
                didFlingToPage = true
            }
            return false
        }
    }

    init {
        gestureDetector = GestureDetectorCompat(context, flingGestureListener)
    }

    private fun scrollToNextItem(direction: Int) {
        findParentViewPager()?.let { viewPager ->
            val nextItem = viewPager.currentItem + direction
            viewPager.setCurrentItem(nextItem, true)
        }
    }

    private fun findParentViewPager(): ViewPager2? {
        var current: ViewParent = this
        while (current.parent != null) {
            current = current.parent
            if (current is ViewPager2) {
                return current
            }
        }
        return null
    }

    private fun getInitialScrolledToBorder(direction: Int): Boolean {
        return when (direction) {
            -1 -> initialScrolledToLeftBorder
            1 -> initialScrolledToRightBorder
            else -> error("direction must be -1 or 1")
        }
    }

}