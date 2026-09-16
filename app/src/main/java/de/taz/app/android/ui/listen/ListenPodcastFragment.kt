package de.taz.app.android.ui.listen

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.audioPlayer.AudioPlayerItem
import de.taz.app.android.audioPlayer.UiState
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.databinding.FragmentListenPodcastBinding
import kotlinx.coroutines.launch

class ListenPodcastFragment : ViewBindingFragment<FragmentListenPodcastBinding>() {
    private val viewModel: ListenPodcastViewModel by viewModels()

    private val podcastHeaderAdapter = PodcastHeaderAdapter(
        onPodcastSelected = { podcast -> viewModel.selectPodcast(podcast) }
    )
    private lateinit var episodeListAdapter: EpisodeListAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val audioPlayerService = AudioPlayerService.getInstance(requireContext().applicationContext)
        episodeListAdapter = EpisodeListAdapter { episode ->
            val currentItem = audioPlayerService.getCurrent()
            val isCurrent = currentItem?.type == AudioPlayerItem.Type.PODCAST &&
                    currentItem.playableKey == episode.id.toString()

            if (isCurrent) {
                audioPlayerService.toggleAudioPlaying()
            } else {
                audioPlayerService.playPodcast(episode)
            }
        }

        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        val concatAdapter = ConcatAdapter(podcastHeaderAdapter, episodeListAdapter)
        viewBinding?.podcastRecyclerView?.apply {
            adapter = concatAdapter
            layoutManager = LinearLayoutManager(context)

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = layoutManager as LinearLayoutManager
                    val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                    val totalItemCount = layoutManager.itemCount

                    if (lastVisibleItemPosition >= totalItemCount - 3) {
                        recyclerView.post {
                            viewModel.loadMoreEpisodes()
                        }
                    }
                }
            })
        }
    }

    private fun observeViewModel() {
        val audioPlayerService = AudioPlayerService.getInstance(requireContext().applicationContext)
        viewBinding?.let { binding ->
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        viewModel.uiState.collect { state ->
                            binding.loadingProgress.isVisible = state is ListenPodcastUiState.Loading
                            binding.errorText.isVisible = state is ListenPodcastUiState.Error && episodeListAdapter.itemCount == 0
                            binding.podcastRecyclerView.isVisible = state is ListenPodcastUiState.Success || 
                                    (state is ListenPodcastUiState.Loading && episodeListAdapter.itemCount > 0) ||
                                    (state is ListenPodcastUiState.Error && episodeListAdapter.itemCount > 0)
                        }
                    }

                    launch {
                        viewModel.podcasts.collect { podcasts ->
                            podcastHeaderAdapter.submitList(podcasts)
                        }
                    }

                    launch {
                        viewModel.selectedPodcast.collect { podcast ->
                            podcastHeaderAdapter.setSelectedPodcast(podcast?.id)
                        }
                    }

                    launch {
                        viewModel.episodeItems.collect { items ->
                            episodeListAdapter.submitList(items)
                        }
                    }

                    launch {
                        audioPlayerService.uiState.collect { state ->
                            val isPlaying = state.getPlayerStateOrNull() is UiState.PlayerState.Playing
                            val currentItem = audioPlayerService.getCurrent()
                            val episodeId = if (currentItem?.type == AudioPlayerItem.Type.PODCAST) {
                                currentItem.playableKey?.toIntOrNull()
                            } else null
                            episodeListAdapter.setPlayingState(episodeId, isPlaying)
                        }
                    }

                    launch {
                        audioPlayerService.currentItem.collect { currentItem ->
                            val isPlaying = audioPlayerService.isPlaying()
                            val episodeId = if (currentItem?.type == AudioPlayerItem.Type.PODCAST) {
                                currentItem.playableKey?.toIntOrNull()
                            } else null
                            episodeListAdapter.setPlayingState(episodeId, isPlaying)
                        }
                    }

                    launch {
                        audioPlayerService.progress.collect { progress ->
                            if (progress != null) {
                                val currentItem = audioPlayerService.getCurrent()
                                if (currentItem?.type == AudioPlayerItem.Type.PODCAST) {
                                    val episodeId = currentItem.playableKey?.toIntOrNull()
                                    val fraction = progress.currentMs.toFloat() / progress.totalMs.toFloat()
                                    episodeListAdapter.setProgress(episodeId, fraction)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
