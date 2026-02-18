package de.taz.app.android.util

import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.behavior.HideViewOnScrollBehavior

private const val ANIMATION_DURATION_MS = 150L

// Thresholds define the percentage of the the visible height of the bar required to let the
// animator finish the expansion or retraction of the bar.
private const val SCROLL_UP_THRESHOLD = 0.01f
private const val SCROLL_DOWN_THRESHOLD = 0.5f

fun View.setBottomNavigationBehavior(behavior: HideViewOnScrollBehavior<View>?) {
    val coordinatorLayoutParams = layoutParams as? CoordinatorLayout.LayoutParams
    if (coordinatorLayoutParams != null) {
        coordinatorLayoutParams.behavior = behavior
        layoutParams = coordinatorLayoutParams
    }
}

fun View.getHideViewOnScrollBehavior(): HideViewOnScrollBehavior<View>? {
    val coordinatorLayoutParams = layoutParams as? CoordinatorLayout.LayoutParams
    return coordinatorLayoutParams?.behavior as? HideViewOnScrollBehavior
}