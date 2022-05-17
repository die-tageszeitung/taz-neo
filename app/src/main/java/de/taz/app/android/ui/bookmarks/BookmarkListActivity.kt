package de.taz.app.android.ui.bookmarks

import android.util.Log
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityBookmarksBinding
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.bottomNavigationBack
import de.taz.app.android.ui.navigation.setupBottomNavigation

class BookmarkListActivity : ViewBindingActivity<ActivityBookmarksBinding>() {

    override fun onResume() {
        Log.d("!!!","BookmarkListActivity.onResume")
        super.onResume()

        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.Bookmark
        )
    }

    override fun onBackPressed() {
        bottomNavigationBack()
    }

    override fun onDestroy() {

        Log.d("!!!","BookmarkListActivity.onDestroy")
        super.onDestroy()
    }
}