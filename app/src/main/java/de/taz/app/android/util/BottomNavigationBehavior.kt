package de.taz.app.android.util

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import kotlin.math.max
import kotlin.math.min

private const val ANIMATION_DURATION_MS = 150L

// Thresholds define the percentage of the the visible height of the bar required to let the
// animator finish the expansion or retraction of the bar.
private const val SCROLL_UP_THRESHOLD = 0.01f
private const val SCROLL_DOWN_THRESHOLD = 0.5f

fun View.setBottomNavigationBehavior(behavior: BottomNavigationBehavior<View>?) {
    val coordinatorLayoutParams = layoutParams as? CoordinatorLayout.LayoutParams
    if (coordinatorLayoutParams != null) {
        coordinatorLayoutParams.behavior = behavior
        layoutParams = coordinatorLayoutParams
    }
}

fun View.getBottomNavigationBehavior(): BottomNavigationBehavior<View>? {
    val coordinatorLayoutParams = layoutParams as? CoordinatorLayout.LayoutParams
    return coordinatorLayoutParams?.behavior as? BottomNavigationBehavior
}

class BottomNavigationBehavior<V : View>(context: Context, attrs: AttributeSet) :
    CoordinatorLayout.Behavior<V>(context, attrs) {

    private val offsetAnimator: ValueAnimator = ValueAnimator().apply {
        interpolator = DecelerateInterpolator()
        duration = ANIMATION_DURATION_MS
    }
    private var translationOnScrollStart = 0f

    fun initialize(view: View) {
        view.translationY = 0f
        view.isVisible = true
    }

    fun expand(view: View, animate: Boolean) {
        offsetAnimator.cancel()
        if (animate) {
            animateBarVisibility(view, true)
        } else {
            view.translationY = 0f
        }
    }

    fun collapse(view: View, animate: Boolean) {
        offsetAnimator.cancel()
        if (animate) {
            animateBarVisibility(view, false)
        } else {
            view.translationY = view.height.toFloat()
        }
    }

    fun getVisibleHeight(view: View): Int {
        return (view.height - view.translationY).toInt()
    }

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

        if (!child.isVisible) {
            initialize(child)
        }
        offsetAnimator.cancel()
        translationOnScrollStart = child.translationY

        return true
    }

    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        type: Int
    ) {
        val currentTranslation = child.translationY
        val visibleHeight = child.height - currentTranslation

        if (translationOnScrollStart < currentTranslation) {
            // The user did scroll down on the nested content
            // Hide the bar when the threshold is met
            val hideBar = visibleHeight <= child.height * SCROLL_DOWN_THRESHOLD
            animateBarVisibility(child, isVisible = !hideBar)
        } else {
            // The user did (or tried to) scroll up on the nested content
            val showBar = visibleHeight >= child.height * SCROLL_UP_THRESHOLD
            animateBarVisibility(child, isVisible = showBar)

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

        if (child.isVisible) {
            child.translationY = max(0f, min(child.height.toFloat(), child.translationY + dy))
        }
    }

    private fun animateBarVisibility(child: View, isVisible: Boolean) {
        offsetAnimator.cancel()

        val childIsFullyVisible = (child.translationY == 0f)
        val childIsFullyHidden = (child.translationY == child.height.toFloat())

        // Abort if we are already in our target state
        if (isVisible && childIsFullyVisible || !isVisible && childIsFullyHidden) {
            return
        }

        val currentTranslation = child.translationY
        val targetTranslation = when (isVisible) {
            true -> 0f
            false -> child.height.toFloat()
        }

        offsetAnimator.apply {
            removeAllUpdateListeners()
            addUpdateListener {
                child.translationY = it.animatedValue as Float
            }
            setFloatValues(currentTranslation, targetTranslation)
            start()
        }
    }
}