package de.taz.app.android.ui.playlist

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.audioPlayer.AudioPlayerViewController
import de.taz.app.android.audioPlayer.Playlist
import de.taz.app.android.audioPlayer.PlaylistAdapter
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityPlaylistBinding
import de.taz.app.android.monkey.disableActivityAnimations
import de.taz.app.android.persistence.repository.PlaylistRepository
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.bottomNavigationBack
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch

class PlaylistActivity:
    ViewBindingActivity<ActivityPlaylistBinding>() {

    private val audioPlayerViewController = AudioPlayerViewController(this)
    private lateinit var audioPlayerService: AudioPlayerService
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var tracker: Tracker

 //   private val _persistedPlaylistState: MutableStateFlow<Playlist> = MutableStateFlow(Playlist.EMPTY)
    private var isPlaylistInitialized = false

    private val log by Log

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableActivityAnimations()
        audioPlayerService = AudioPlayerService.getInstance(applicationContext)
        playlistAdapter = PlaylistAdapter(audioPlayerService)
        playlistRepository = PlaylistRepository.getInstance(applicationContext)
        tracker = Tracker.getInstance(applicationContext)


    }

    override fun onResume() {
        super.onResume()
        log.error("!!! resumed")
        // init playlist state:
        lifecycleScope.launch {
            audioPlayerService.persistedPlaylistState.collect { playlist ->
                log.error("!!! collected playlist: $playlist")
                // save newPlaylist (if initialized):
                if (isPlaylistInitialized) {
                    playlistRepository.sync(playlist)
                }
                playlistAdapter.submitPlaylist(playlist)
                setupUserInteractionsHandlers(playlist)
                isPlaylistInitialized = true
            }
        }

        viewBinding.playlistRv.adapter = playlistAdapter

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

    private fun setupUserInteractionsHandlers(playlistData: Playlist) {
        viewBinding.apply {
            playlistEmpty.isVisible = playlistData.items.isEmpty()

            if (playlistData.currentItemIdx != -1) {
                playlistRv.smoothScrollToPosition(playlistData.currentItemIdx)
            }
            deleteLayout.setOnClickListener {
                audioPlayerService.clearPlaylist()
                //_persistedPlaylistState.value = Playlist(currentItemIdx = -1, items = emptyList())
            }
        }
    }
}