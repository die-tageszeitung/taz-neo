package de.taz.app.android.audioPlayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.taz.app.android.AbstractTazApplication
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.AudioPlayerPlayable
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Convenience wrapper around the [AudioPlayerService] that may be used from Fragments showing an [AudioPlayerPlayable].
 *
 * Note: this ViewModel *must not* be bound to an Activity but shall only be used on Fragments with `by viewModel<AudioPlayerViewModel>()`
 *
 */
abstract class AudioPlayerViewModel<PLAYABLE: AudioPlayerPlayable>(androidApplication: Application) : AndroidViewModel(androidApplication) {
    protected val log by Log

    protected val application = androidApplication as AbstractTazApplication
    protected val audioPlayerService = AudioPlayerService.getInstance(application.applicationContext)

    var visibleIssueKey: AbstractIssueKey? = null

    private val visiblePlayable: MutableStateFlow<PLAYABLE?> = MutableStateFlow(null)
    private val visiblePlayableKey = visiblePlayable.map { it?.audioPlayerPlayableKey }

    private val _errorMessageFlow = MutableStateFlow<String?>(null)
    val errorMessageFlow: StateFlow<String?> = _errorMessageFlow.asStateFlow()

    val isPlayerVisible = audioPlayerService.uiState.map { it.isPlayerVisible() }

    /**
     * True if the currently visible article is active within the audio player.
     * Note, that does not mean it is playing.
     */
    val isActiveAudio: Flow<Boolean> = combine(
        visiblePlayableKey, isPlayerVisible, audioPlayerService.currentItem
    ) { playableKey: String?, isPlayerVisible: Boolean, audioPlayerItem: AudioPlayerItem? ->
        isPlayerVisible && audioPlayerItem != null && audioPlayerItem.playableKey == playableKey
    }

    fun setVisible(articleStub: PLAYABLE) {
        visiblePlayable.value = articleStub
    }

    /**
     * Handle actions on the bottom navigation bar.
     * Will start to play the currently visible article,
     * or dismiss the player if it is was already showing that article.
     */
    fun handleOnAudioActionOnVisible() {
        viewModelScope.launch {
            val playable = visiblePlayable.value
            if (playable != null) {
                val isVisiblePlayableActive = isActiveAudio.first()
                if (isVisiblePlayableActive) {
                    audioPlayerService.dismissPlayer()
                } else {
                    try {
                        play(playable)
                    } catch (e: Exception) {
                        log.error("Could not play article audio (${playable.audioPlayerPlayableKey})", e)
                        _errorMessageFlow.value = application.getString(R.string.toast_unknown_error)
                    }
                }
            } else {
                log.error("handleOnAudioActionOnVisible() was called before setVisible()")
                _errorMessageFlow.value = application.getString(R.string.toast_unknown_error)
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageFlow.value = null
    }

    abstract fun play(playable: PLAYABLE)
}

class SearchHitAudioPlayerViewModel(androidApplication: Application) :
    AudioPlayerViewModel<SearchHit>(androidApplication) {

    override fun play(playable: SearchHit) {
        audioPlayerService.playSearchHit(playable)
    }
}

class ArticleAudioPlayerViewModel(androidApplication: Application) :
    AudioPlayerViewModel<ArticleOperations>(androidApplication) {

    override fun play(playable: ArticleOperations) {
        audioPlayerService.playArticle(playable.key)
    }
}
