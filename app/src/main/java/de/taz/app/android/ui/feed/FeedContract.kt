package de.taz.app.android.ui.feed

import android.view.MenuItem

interface FeedContract {
    interface View
    interface Presenter {
        suspend fun onRefresh()
        fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean)
    }
    interface DataController
}