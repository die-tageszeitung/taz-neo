package de.taz.app.android.util

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat

class BottomNavigationBehavior<V : View>(context: Context, attrs: AttributeSet) :
    CoordinatorLayout.Behavior<V>(context, attrs) {

    private var isInitialized = false

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        if (!isInitialized) {
            initializeBottomNavigationView(child)
            isInitialized = true
        }
        return axes == ViewCompat.SCROLL_AXIS_VERTICAL
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int
    ) {
        super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)

        if (isInitialized) {
            // when scrolling up:
            if (dy <= 0) {
                showBottomNavigationView(child)
            } else {
                hideBottomNavigationView(child)
            }
        }
    }

    private fun hideBottomNavigationView(view: View) {
        view.animate().translationY(view.height.toFloat())
    }

    private fun showBottomNavigationView(view: View) {
        view.animate().translationY(0f)
    }

    private fun initializeBottomNavigationView(view: View) {
        view.translationY = view.height.toFloat()
        view.visibility = View.VISIBLE
    }
}