package de.taz.app.android.monkey

import androidx.core.view.get
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * Monkey patching ViewPager2 adding a function to reduce the drag sensitivity.
 * Kudos: https://medium.com/@al.e.shevelev/how-to-reduce-scroll-sensitivity-of-viewpager2-widget-87797ad02414
 */
fun ViewPager2.reduceDragSensitivity(factor: Int = 8) {
    val recyclerView = getRecyclerView()
    val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
    touchSlopField.isAccessible = true
    val touchSlop = touchSlopField.get(recyclerView) as Int
    touchSlopField.set(recyclerView, touchSlop * factor)
}

fun ViewPager2.getRecyclerView(): RecyclerView = (get(0) as? RecyclerView)
    ?: throw IllegalStateException("ViewPagers first child is expected to be the RecyclerView")