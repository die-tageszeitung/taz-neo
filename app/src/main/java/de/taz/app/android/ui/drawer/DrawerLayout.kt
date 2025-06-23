package de.taz.app.android.ui.drawer

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import de.taz.app.android.tracking.Tracker

class DrawerLayout @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null
) : DrawerLayout(context, attributeSet) {

    private val tracker: Tracker = Tracker.getInstance(context.applicationContext)

    private val exclusionRectList = listOf(
        // exclusion rects are only allowed to be on 20% of the screen height
        Rect(
            0, 0,
            resources.displayMetrics.widthPixels / 4,
            resources.displayMetrics.heightPixels / 5
        )
    )

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // exclude top left from swipe gestures to allow opening the drawer by swipe there:
        if (Build.VERSION.SDK_INT >= 29) {
            systemGestureExclusionRects = exclusionRectList
        }
    }

    // Setup drawer tracking
    init {
        addDrawerListener(object : DrawerListener {

            private var isDragging = false

            override fun onDrawerOpened(drawerView: View) {
                if (isDragging) {
                    tracker.trackDrawerOpenEvent(dragged = true)
                }
                isDragging = false
            }

            override fun onDrawerClosed(drawerView: View) {
                isDragging = false
            }

            override fun onDrawerStateChanged(newState: Int) {
                when (newState) {
                    STATE_DRAGGING -> isDragging = true
                    STATE_IDLE -> isDragging = false
                    STATE_SETTLING -> Unit
                }
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) = Unit
        })
    }
}