package de.taz.app.android.ui.bookmarks

import android.annotation.SuppressLint
import de.taz.app.android.audioPlayer.AudioPlayerViewController
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityBookmarksBinding
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.bottomNavigationBack
import de.taz.app.android.ui.navigation.setupBottomNavigation

class BookmarkListActivity : ViewBindingActivity<ActivityBookmarksBinding>() {

    @Suppress("unused")
    private val audioPlayerViewController = AudioPlayerViewController(this)

    override fun onResume() {
        super.onResume()

        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.Bookmark
        )
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (audioPlayerViewController.onBackPressed()) {
            return
        }
        bottomNavigationBack()
    }
}