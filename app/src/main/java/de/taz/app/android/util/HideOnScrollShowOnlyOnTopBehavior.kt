package de.taz.app.android.util

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior

/**
 * Use HideBottomViewOnScrollBehaviour but only show the view if the scrollview is scrolled to top
 */
class HideOnScrollShowOnlyOnTopBehavior<V : View>(
    context: Context?,
    attrs: AttributeSet?,
) : HideBottomViewOnScrollBehavior<V>(
    context, attrs,
) {
    private val log by Log

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        if (dyConsumed < 0 && target.scrollY != 0) {
            return
        }

        super.onNestedScroll(
            coordinatorLayout,
            child,
            target,
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            type,
            consumed
        )
    }
}
