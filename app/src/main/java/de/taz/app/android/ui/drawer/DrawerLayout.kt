package de.taz.app.android.ui.drawer

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import de.taz.app.android.R
import de.taz.app.android.util.Log

class DrawerLayout @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null, defStyle: Int = 0
) : DrawerLayout(context, attributeSet, defStyle) {

    private val log by Log
    var drawerLogoBoundingBox: Rect? = null

    fun updateDrawerLogoBoundingBox(width: Int, height: Int) {
        drawerLogoBoundingBox = Rect(
            0,
            resources.getDimensionPixelSize(R.dimen.drawer_logo_margin_top),
            width,
            resources.getDimensionPixelSize(R.dimen.drawer_logo_margin_top) + height
        )
    }

    /**
     * catch touch events on floating logo and open drawer
     * TODO Verify this approach handles accessibility correctly
     */
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            if (!isDrawerOpen(GravityCompat.START)) {
                if (drawerLogoBoundingBox?.contains(ev.x.toInt(), ev.y.toInt()) == true) {
                    log.debug("TouchEvent ${ev.x}, ${ev.y} intercepted - opening drawer")
                    openDrawer(GravityCompat.START)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

}