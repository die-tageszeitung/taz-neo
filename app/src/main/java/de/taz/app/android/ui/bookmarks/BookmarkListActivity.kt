package de.taz.app.android.ui.bookmarks

import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityBookmarksBinding
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation

class BookmarkListActivity : ViewBindingActivity<ActivityBookmarksBinding>() {

    override fun onResume() {
        super.onResume()

        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.Bookmark
        )
    }

}