package de.taz.app.android.ui.feed

import android.view.MenuItem
import de.taz.app.android.R
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.ui.bookmarks.BookmarksFragment

class FeedPresenter : FeedContract.Presenter, BasePresenter<FeedFragment, FeedDataController>(FeedDataController::class.java) {

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        if (menuItem.itemId == R.id.bottom_navigation_action_bookmark) {
            getView()?.getMainView()?.showMainFragment(BookmarksFragment())
        }
    }
}