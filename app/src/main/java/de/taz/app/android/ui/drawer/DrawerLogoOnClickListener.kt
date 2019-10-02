package de.taz.app.android.ui.drawer

import android.view.View
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

const val DRAWER_GRAVITY = GravityCompat.START

class DrawerLogoOnClickListener(private val drawerLayout: DrawerLayout): View.OnClickListener {
    override fun onClick(p0: View?) {
        if (drawerLayout.isDrawerOpen(DRAWER_GRAVITY))
            drawerLayout.closeDrawer(DRAWER_GRAVITY)
        else
            drawerLayout.openDrawer(DRAWER_GRAVITY)
    }
}