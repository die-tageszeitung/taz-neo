package de.taz.app.android.monkey

import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.google.android.material.behavior.HideViewOnScrollBehavior.EDGE_LEFT

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


class LogoScrollBehavior(
    onScrolledIn: () -> Unit,
    onScrolledOut: () -> Unit
): HideViewOnScrollBehavior<View>() {
    init {
        setViewEdge(EDGE_LEFT)

        addOnScrollStateChangedListener { _, scrollState ->
            if (scrollState == HideViewOnScrollBehavior.STATE_SCROLLED_IN) {
                onScrolledIn()
            } else {
                onScrolledOut()
            }
        }
    }
}

/**
 * Sets up the scroll behavior for the logo that toggles between burger and feed logo
 * based on scroll position.
 */
fun View.setupLogoScrollBehavior(
    enabled: Boolean,
    logoScrollBehavior: LogoScrollBehavior,
) {
    if (enabled) {
        setBottomNavigationBehavior(logoScrollBehavior)
    } else {
        setBottomNavigationBehavior(null)
    }
}