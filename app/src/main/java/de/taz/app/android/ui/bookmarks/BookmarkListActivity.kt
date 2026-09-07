package de.taz.app.android.ui.bookmarks

import android.os.Bundle
import androidx.activity.addCallback
import de.taz.app.android.audioPlayer.AudioPlayerViewController
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityBookmarksBinding
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.bottomNavigationBack
import de.taz.app.android.ui.navigation.setupBottomNavigation

class BookmarkListActivity : ViewBindingActivity<ActivityBookmarksBinding>() {

    @Suppress("UNUSED") // this is necessary so the audio player is shown
    private val audioPlayerViewController = AudioPlayerViewController(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) {
            bottomNavigationBack()
        }
    }

    override fun onResume() {
        super.onResume()

        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.Bookmark
        )
    }
}