package de.taz.app.android.ui.webview

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import kotlin.math.sign

class NestedScrollableHost @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : FrameLayout(context, attributeSet) {
    private var touchSlop = 0
    private var initialX = 0.0f
    private var initialY = 0.0f

    private fun parentViewPager(): ViewPager2 {
        var parentView = this.parent as View?
        while (parentView !is ViewPager2) parentView = parentView?.parent as? View
        return parentView
    }

    private fun child(): View? {
        return (if (this.childCount > 0) this.getChildAt(0) else null)
    }

    init {
        this.touchSlop = ViewConfiguration.get(this.context).scaledTouchSlop
    }

    private fun canChildScroll(orientation: Int, delta: Float): Boolean {
        val direction = sign(-delta.toDouble()).toInt()
        val child = this.child() ?: return false

        if (orientation == 0) return child.canScrollHorizontally(direction)
        if (orientation == 1) return child.canScrollVertically(direction)

        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        this.handleInterceptTouchEvent(ev)
        return super.onInterceptTouchEvent(ev)
    }

    private fun handleInterceptTouchEvent(event: MotionEvent) {
        val viewPager = this.parentViewPager()

        val orientation = viewPager.orientation

        // Early return if child can't scroll in same direction as parent
        if (!this.canChildScroll(orientation, -1.0f) && !this.canChildScroll(
                orientation,
                1.0f
            )
        ) return

        if (event.action == MotionEvent.ACTION_DOWN) {
            this.initialX = event.x
            this.initialY = event.y
            this.parent.requestDisallowInterceptTouchEvent(true)
        } else if (event.action == MotionEvent.ACTION_MOVE) {
            val dx = event.x - this.initialX
            val dy = event.y - this.initialY
            val isVpHorizontal = (orientation == ViewPager2.ORIENTATION_HORIZONTAL)

            // assuming ViewPager2 touch-slop is 2x touch-slop of child
            val scaleDx = (abs(dx.toDouble()) * (if (isVpHorizontal) 0.5f else 1.0f)).toFloat()
            val scaleDy = (abs(dy.toDouble()) * (if (isVpHorizontal) 1.0f else 0.5f)).toFloat()

            if (scaleDx > this.touchSlop || scaleDy > this.touchSlop) {
                if (isVpHorizontal == (scaleDy > scaleDx)) {
                    // Gesture is perpendicular, allow all parents to intercept
                    this.parent.requestDisallowInterceptTouchEvent(false)
                } else {
                    // Gesture is parallel, query child if movement in that direction is possible
                    if (this.canChildScroll(orientation, (if (isVpHorizontal) dx else dy))) {
                        this.parent.requestDisallowInterceptTouchEvent(true)
                    } else {
                        // Child cannot scroll, allow all parents to intercept
                        this.parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
        }
    }
}
