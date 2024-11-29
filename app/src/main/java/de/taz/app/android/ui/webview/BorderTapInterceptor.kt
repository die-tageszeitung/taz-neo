package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.widget.FrameLayout
import de.taz.app.android.R
import de.taz.app.android.ui.ViewBorder
import de.taz.app.android.util.Log

class BorderTapInterceptor @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : FrameLayout(context, attributeSet) {

    private val gestureDetector: GestureDetector

    /**
     * returns true if the event was consumed - otherwise false
     */
    var onBorderTapListener: ((ViewBorder) -> Boolean)? = null

    private val gestureListener = object : SimpleOnGestureListener() {
        // call onBordertapListener if clicks on the border
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            return handleTap(e.x) || super.onSingleTapConfirmed(e)
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
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && event.pointerCount == 1) {
            return gestureDetector.onTouchEvent(event) || super.onInterceptTouchEvent(event)
        }

        return super.onInterceptTouchEvent(event)
    }

    init {
        gestureDetector = GestureDetector(context, gestureListener)
    }
}