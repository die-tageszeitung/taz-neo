package de.taz.app.android.ui.feed

import android.view.MenuItem

interface FeedContract {
    interface View
    interface Presenter {
        fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean)
    }
    interface DataController
}