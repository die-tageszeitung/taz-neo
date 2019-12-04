package de.taz.app.android.ui.home

import android.view.MenuItem

interface HomeContract {
    interface View {
        fun enableRefresh()
        fun disableRefresh()
    }
    interface Presenter {
        suspend fun onRefresh()
        fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean)
    }
    interface DataController
}