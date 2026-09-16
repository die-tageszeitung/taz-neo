package de.taz.app.android.ui.listen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.models.Podcast
import de.taz.app.android.api.models.PodcastEpisode
import de.taz.app.android.persistence.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val AMOUNT_EPISODES_TO_BE_LOADED = 10

class ListenPodcastViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val podcastRepository = PodcastRepository.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow<ListenPodcastUiState>(ListenPodcastUiState.Loading)
    val uiState: StateFlow<ListenPodcastUiState> = _uiState.asStateFlow()

    private val _podcasts = MutableStateFlow<List<Podcast>>(emptyList())
    val podcasts: StateFlow<List<Podcast>> = _podcasts.asStateFlow()

    private val _selectedPodcast = MutableStateFlow<Podcast?>(null)
    val selectedPodcast: StateFlow<Podcast?> = _selectedPodcast.asStateFlow()

    private val _episodes = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    val episodes: StateFlow<List<PodcastEpisode>> = _episodes.asStateFlow()

    private val _isFetchingMore = MutableStateFlow(false)
    val isFetchingMoreFlow: StateFlow<Boolean> = _isFetchingMore.asStateFlow()

    val episodeItems: StateFlow<List<EpisodeListItem>> =
        combine(_episodes, _isFetchingMore) { episodes, loading ->
            // if loading add the loadingMore item
            episodes.map { EpisodeListItem.Episode(it) } + if (loading) listOf(EpisodeListItem.LoadingMore) else emptyList()
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var currentLimit = AMOUNT_EPISODES_TO_BE_LOADED
    private var isFetchingMore = false

    init {
        observePodcasts()
        fetchPodcasts()
    }

    private fun observePodcasts() {
        viewModelScope.launch {
            podcastRepository.observePodcasts().collect { podcastList ->
                _podcasts.value = podcastList
                
                // Update selected podcast if it's in the list to get potential new episodes
                val currentSelectedId = _selectedPodcast.value?.id
                if (currentSelectedId != null) {
                    podcastList.find { it.id == currentSelectedId }?.let { updated ->
                        _selectedPodcast.value = updated
                        _episodes.value = updated.episodeList
                    }
                } else if (podcastList.isNotEmpty()) {
                    // Initial selection
                    selectPodcast(podcastList[0])
                }
                
                if (podcastList.isNotEmpty()) {
                    _uiState.value = ListenPodcastUiState.Success
                } else if (_uiState.value is ListenPodcastUiState.Success) {
                    // This shouldn't happen if we have data, but if we somehow lost it, maybe show error or loading
                    _uiState.value = ListenPodcastUiState.Loading
                }
            }
        }
    }

    fun fetchPodcasts() {
        viewModelScope.launch {
            if (_podcasts.value.isEmpty()) {
                _uiState.value = ListenPodcastUiState.Loading
            }
            try {
                podcastRepository.refreshPodcasts(currentLimit)
                if (_podcasts.value.isNotEmpty()) {
                    _uiState.value = ListenPodcastUiState.Success
                } else {
                    _uiState.value = ListenPodcastUiState.Error
                }
            } catch (_: Exception) {
                if (_podcasts.value.isEmpty()) {
                    _uiState.value = ListenPodcastUiState.Error
                } else {
                    _uiState.value = ListenPodcastUiState.Success
                }
            }
        }
    }

    fun selectPodcast(podcast: Podcast) {
        _selectedPodcast.value = podcast
        _episodes.value = podcast.episodeList
    }

    fun loadMoreEpisodes() {
        if (isFetchingMore) return
        
        val selected = _selectedPodcast.value ?: return
        if (selected.episodeCnt <= _episodes.value.size) return

        isFetchingMore = true
        _isFetchingMore.value = true
        viewModelScope.launch {
            try {
                currentLimit += AMOUNT_EPISODES_TO_BE_LOADED
                podcastRepository.refreshPodcasts(currentLimit)
            } catch (_: Exception) {
                // Ignore for load more
            } finally {
                isFetchingMore = false
                _isFetchingMore.value = false
            }
        }
    }
}

sealed class ListenPodcastUiState {
    object Loading : ListenPodcastUiState()
    object Success : ListenPodcastUiState()
    object Error : ListenPodcastUiState()
}
