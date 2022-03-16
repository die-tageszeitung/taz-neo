package de.taz.app.android.ui.bookmarks

import android.content.Intent
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityBookmarksBinding
import de.taz.app.android.ui.main.MainActivity
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

    override fun onBackPressed() {
        Intent(
            this,
            MainActivity::class.java
        ).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .apply { startActivity(this) }
        finish()
    }
}