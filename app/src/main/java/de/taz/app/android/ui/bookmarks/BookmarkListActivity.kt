package de.taz.app.android.ui.bookmarks

import android.os.Bundle
import de.taz.app.android.audioPlayer.AudioPlayerViewController
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityBookmarksBinding
import de.taz.app.android.getTazApplication
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.bottomNavigationBack
import de.taz.app.android.ui.navigation.setupBottomNavigation

class BookmarkListActivity : ViewBindingActivity<ActivityBookmarksBinding>() {

    @Suppress("unused")
    private val audioPlayerViewController = AudioPlayerViewController(this)
    private lateinit var tracker: Tracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tracker = getTazApplication().tracker
    }

    override fun onResume() {
        super.onResume()

        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.Bookmark
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        tracker.trackSystemNavigationBackEvent()
        if (audioPlayerViewController.onBackPressed()) {
            return
        }
        bottomNavigationBack()
    }
}