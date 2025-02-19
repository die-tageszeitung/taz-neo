package de.taz.app.android.ui.playlist

import android.annotation.SuppressLint
import android.os.Bundle
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.audioPlayer.AudioPlayerViewController
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityPlaylistBinding
import de.taz.app.android.monkey.disableActivityAnimations
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.bottomNavigationBack
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.util.Log

class PlaylistActivity:
    ViewBindingActivity<ActivityPlaylistBinding>() {

    private val audioPlayerViewController = AudioPlayerViewController(this)
    private lateinit var audioPlayerService: AudioPlayerService
    private val log by Log

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioPlayerService = AudioPlayerService.getInstance(applicationContext)
        log.error("tada !!!")
        disableActivityAnimations()
    }

    override fun onResume() {
        super.onResume()

        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.Playlist
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