package de.taz.app.android.monkey

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/*
 * These functions are used to set the default insets by either the system bars or the displayCutout
 * Use this function to fix visual problems
 */
fun View.setDefaultInsets(
    top: Boolean = true,
    bottom: Boolean = true,
    left: Boolean = true,
    right: Boolean = true,
) {
    // Set Listener
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
        )
        v.updatePadding(
            left = if (left) bars.left else v.paddingLeft,
            top = if (top) bars.top else v.paddingTop,
            right = if (right) bars.right else paddingRight,
            bottom = if(bottom) bars.bottom else paddingBottom,
        )
        insets
    }
}

fun View.setDefaultTopInset() {
    setDefaultInsets(bottom = false, left = false, right = false)
}

fun View.setDefaultBottomInset() {
    setDefaultInsets(top = false, left = false, right = false)
}

fun View.setDefaultHorizontalInsets() {
    setDefaultInsets(top = false, bottom = false)
}

fun View.setDefaultVerticalInsets() {
    setDefaultInsets(left = false, right = false)
}