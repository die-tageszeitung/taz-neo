package de.taz.app.android.ui.listen

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.audioPlayer.Playlist
import de.taz.app.android.audioPlayer.PlaylistAdapter
import de.taz.app.android.audioPlayer.UiState
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.databinding.FragmentListenPlaylistBinding
import de.taz.app.android.persistence.repository.PlaylistRepository
import de.taz.app.android.tracking.Tracker
import kotlinx.coroutines.launch

class ListenPlaylistFragment : ViewBindingFragment<FragmentListenPlaylistBinding>() {

    private lateinit var audioPlayerService: AudioPlayerService
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var tracker: Tracker

    private var isPlaylistInitialized = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        audioPlayerService = AudioPlayerService.getInstance(context.applicationContext)
        playlistAdapter = PlaylistAdapter(audioPlayerService)
        playlistRepository = PlaylistRepository.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)

        // init playlist state:
        lifecycleScope.launch {
            audioPlayerService.persistedPlaylistState.collect { playlist ->
                // save newPlaylist (if initialized):
                if (isPlaylistInitialized) {
                    playlistRepository.sync(playlist)
                }
                playlistAdapter.submitPlaylist(playlist)
                setupUserInteractionsHandlers(playlist)
                isPlaylistInitialized = true
            }
        }

        lifecycleScope.launch {
            audioPlayerService.uiState.collect {
                val playerState = it.getPlayerStateOrNull()
                if (playerState is UiState.PlayerState.Playing || playerState is UiState.PlayerState.Paused || it is UiState.Hidden) {
                    viewBinding?.playlistRv?.adapter?.notifyItemChanged(audioPlayerService.persistedPlaylistState.value.currentItemIdx)
                }
            }
        }
        viewBinding?.playlistRv?.adapter = playlistAdapter
    }

    override fun onResume() {
        super.onResume()
        // If player is running ensure it is the small one at start:
        audioPlayerService.minimizePlayer()
    }

    private fun setupUserInteractionsHandlers(playlistData: Playlist) {
        viewBinding?.apply {
            playlistEmpty.isVisible = playlistData.items.isEmpty()

            if (playlistData.currentItemIdx != -1) {
                playlistRv.smoothScrollToPosition(playlistData.currentItemIdx)
            }
            deleteLayout.setOnClickListener {
                audioPlayerService.clearPlaylist()
            }
        }
    }
}
