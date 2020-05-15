package de.taz.app.android.monkey

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2


/**
 * Monkey patching ViewPager2 adding a function to reduce the drag sensitivity.
 * Kudos: https://medium.com/@al.e.shevelev/how-to-reduce-scroll-sensitivity-of-viewpager2-widget-87797ad02414
 */
fun ViewPager2.reduceDragSensitivity(factor: Int = 8) {
    val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
    recyclerViewField.isAccessible = true
    val recyclerView = recyclerViewField.get(this) as RecyclerView

    val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
    touchSlopField.isAccessible = true
    val touchSlop = touchSlopField.get(recyclerView) as Int
    touchSlopField.set(recyclerView, touchSlop * factor)
}

/**
 * Monkey patching ViewPager2 so that the recyclerview is shown beneath the status bar
 */
fun ViewPager2.moveContentBeneathStatusBar() {
    val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
    recyclerViewField.isAccessible = true
    val recyclerView = recyclerViewField.get(this) as RecyclerView

    setOnApplyWindowInsetsListener { v, insets ->
        (v.layoutParams as ViewGroup.MarginLayoutParams).apply {
            topMargin = 0
            leftMargin = insets.systemWindowInsetLeft
            rightMargin = insets.systemWindowInsetRight
            bottomMargin = insets.systemWindowInsetBottom
        }
        // trigger for recyclerview as well
        for (index in 0 until childCount) getChildAt(index).dispatchApplyWindowInsets(insets)
        insets.consumeSystemWindowInsets()
    }

    recyclerView.setOnApplyWindowInsetsListener { v, insets ->
        val layoutParams = ViewGroup.MarginLayoutParams(v.layoutParams)
        layoutParams.apply {
            topMargin = 0
            leftMargin = insets.systemWindowInsetLeft
            rightMargin = insets.systemWindowInsetRight
            bottomMargin = insets.systemWindowInsetBottom
        }
        v.layoutParams = layoutParams
        insets.consumeSystemWindowInsets()
    }

    requestApplyInsets()
}


/**
 * Monkey patching ViewPager2 so that the recyclerview is shown beneath the status bar
 */
fun ViewGroup.moveContentBeneathStatusBar() {
    setOnApplyWindowInsetsListener { v, insets ->
        (v.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
            topMargin = 0
            leftMargin = insets.systemWindowInsetLeft
            rightMargin = insets.systemWindowInsetRight
            bottomMargin = insets.systemWindowInsetBottom
        }
        // trigger for recyclerview as well
        for (index in 0 until childCount) getChildAt(index).dispatchApplyWindowInsets(insets)
        insets.consumeSystemWindowInsets()
    }

    requestApplyInsets()
}