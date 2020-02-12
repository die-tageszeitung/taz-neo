package de.taz.app.android.util

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import kotlin.math.max
import kotlin.math.min

class BottomNavigationBehavior<V : View>(context: Context, attrs: AttributeSet) :
    CoordinatorLayout.Behavior<V>(context, attrs) {

    private var offsetAnimator: ValueAnimator? = null
    private var isInitialized = false

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        if (axes != ViewCompat.SCROLL_AXIS_VERTICAL) {
            return false
        }

        if (!isInitialized) {
            initializeBottomNavigationView(child)
            isInitialized = true
        }

        offsetAnimator?.cancel()

        return true
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View, type: Int) {

        val currTranslation = child.translationY
        val childHalfHeight = child.height * 0.5f

        // translate down
        if (currTranslation >= childHalfHeight) {
            animateBarVisibility(child, isVisible = false)
        }
        // translate up
        else {
            animateBarVisibility(child, isVisible = true)
        }

    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int
    ) {
        super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)

        if (isInitialized) {
            child.translationY = max(0f, min(child.height.toFloat(), child.translationY + dy))
        }
    }

    private fun animateBarVisibility(child: View, isVisible: Boolean) {
        if (offsetAnimator == null) {
            offsetAnimator = ValueAnimator().apply {
                interpolator = DecelerateInterpolator()
                duration = 150L
            }

            offsetAnimator?.addUpdateListener {
                child.translationY = it.animatedValue as Float
            }
        } else {
            offsetAnimator?.cancel()
        }
        val targetTranslation = if (isVisible) {
            0f
        } else {
            child.height.toFloat()
        }
        offsetAnimator?.setFloatValues(child.translationY, targetTranslation)
        offsetAnimator?.start()
    }

    private fun initializeBottomNavigationView(view: View) {
        view.translationY = view.height.toFloat()
        view.visibility = View.VISIBLE
    }
}