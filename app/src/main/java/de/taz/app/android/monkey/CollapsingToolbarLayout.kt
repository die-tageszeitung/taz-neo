package de.taz.app.android.monkey

import androidx.core.view.updateLayoutParams
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout

fun CollapsingToolbarLayout.pinToolbar(isPinned: Boolean) {
    updateLayoutParams<AppBarLayout.LayoutParams> {
        scrollFlags = if (isPinned) {
            AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
        } else {
            AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
        }
    }
}