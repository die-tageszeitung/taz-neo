package de.taz.app.android.monkey

import com.google.android.material.appbar.AppBarLayout

fun AppBarLayout.isExpanded(): Boolean {
    return height - bottom == 0
}

fun AppBarLayout.isCollapsed(): Boolean {
    return bottom  + totalScrollRange == height
}

enum class AppBarLayoutState {
    EXPANDED,
    CLOSED,
    CLOSING,
    EXPANDING,
}

fun AppBarLayout.addOnStateChangeListener(listenerFun: (AppBarLayoutState) -> Unit) {
    var previousState: AppBarLayoutState? = null
    var previousOffset: Int? = null

    addOnOffsetChangedListener { _, i ->
        // ignore if already triggered
        if (i == previousOffset) {
            return@addOnOffsetChangedListener
        }
        previousOffset = i

        val currentState = when (i) {
            // completely expanded
            0 -> AppBarLayoutState.EXPANDED

            // completely closed
            -totalScrollRange -> AppBarLayoutState.CLOSED

            // something in between
            else -> {
                if (i > previousOffset) {
                    AppBarLayoutState.EXPANDING
                } else if (i < previousOffset){
                    AppBarLayoutState.CLOSING
                } else {
                    return@addOnOffsetChangedListener
                }
            }
        }

        // ignore if same state
        if (previousState == currentState) {
            return@addOnOffsetChangedListener
        }

        previousState = currentState

        listenerFun(currentState)
    }
}