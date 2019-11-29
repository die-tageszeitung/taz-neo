package de.taz.app.android.monkey

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