package de.taz.app.android.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView

/**
 * [NestedScrollView] that can be locked from scrolling.
 * It is used in the multi column mode.
 */
class LockableNestedScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : NestedScrollView(context, attrs) {

    /**
     * Set to false the web view will handled the scrolling itself
     */
    var scrollingEnabled = true

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return scrollingEnabled && super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return scrollingEnabled && super.onTouchEvent(ev)
    }
}