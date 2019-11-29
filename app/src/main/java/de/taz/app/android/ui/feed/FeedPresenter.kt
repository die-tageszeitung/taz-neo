package de.taz.app.android.ui.feed

import android.view.MenuItem
import de.taz.app.android.R
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.ui.bookmarks.BookmarksFragment
import de.taz.app.android.ui.settings.SettingsOuterFragment

class FeedPresenter : FeedContract.Presenter, BasePresenter<FeedFragment, FeedDataController>(FeedDataController::class.java) {

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_bookmark -> getView()?.getMainView()?.showMainFragment(BookmarksFragment())
            R.id.bottom_navigation_action_settings -> getView()?.getMainView()?.showMainFragment(SettingsOuterFragment())
        }
    }
}