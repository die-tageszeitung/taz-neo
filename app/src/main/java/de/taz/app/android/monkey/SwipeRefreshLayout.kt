package de.taz.app.android.monkey

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout


/**
 * Monkey patching ViewPager2 adding a function to reduce the drag sensitivity.
 * Kudos: https://medium.com/@al.e.shevelev/how-to-reduce-scroll-sensitivity-of-viewpager2-widget-87797ad02414
 */
fun SwipeRefreshLayout.reduceDragSensitivity(factor: Int = 8) {
    val touchSlopField = SwipeRefreshLayout::class.java.getDeclaredField("mTouchSlop")
    touchSlopField.isAccessible = true
    val touchSlop = touchSlopField.get(this) as Int
    touchSlopField.set(this, touchSlop * factor)
}

/**
 * Monkey patching SwipeRefreshLayout adding a function to allow setting isRefreshing and calling the onRefreshListener
 */
fun SwipeRefreshLayout.setRefreshingWithCallback(isRefreshing: Boolean) {
    setRefreshing(isRefreshing)
    val listenerField = SwipeRefreshLayout::class.java.getDeclaredField("mListener")
    listenerField.isAccessible = true
    (listenerField[this] as? SwipeRefreshLayout.OnRefreshListener)?.onRefresh()
}