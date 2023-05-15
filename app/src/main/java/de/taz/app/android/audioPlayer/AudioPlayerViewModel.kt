package de.taz.app.android.audioPlayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.taz.app.android.AbstractTazApplication
import de.taz.app.android.R
import de.taz.app.android.api.models.ArticleStub
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
 * Convenience wrapper around the [AudioPlayerService] that may be used from
 *
 * Note: this ViewModel *must not* be bound the an Activity but shall only be used on Fragments with `by viewModel<AudioPlayerViewModel>()`
 */
class AudioPlayerViewModel(androidApplication: Application) : AndroidViewModel(androidApplication) {
    private val log by Log

    private val application = androidApplication as AbstractTazApplication
    private val audioPlayerService = AudioPlayerService.getInstance(application.applicationContext)

    private val visibleArticleStub: MutableStateFlow<ArticleStub?> = MutableStateFlow(null)
    private val visibleArticleFileName = visibleArticleStub.map { it?.articleFileName }

    private val _errorMessageFlow = MutableStateFlow<String?>(null)
    val errorMessageFlow: StateFlow<String?> = _errorMessageFlow.asStateFlow()

    /**
     * True if the currently visible article is active within the audio player.
     * Note, that does not mean it is playing.
     */
    val isActiveAudio: Flow<Boolean> = combine(
        visibleArticleFileName, audioPlayerService.uiState
    ) { articleFileName: String?, state: UiState ->
        when (state) {
            is UiState.Error -> state.articleAudio?.isArticleFileName(articleFileName) ?: false
            UiState.Hidden -> false
            is UiState.Loading -> state.articleAudio?.isArticleFileName(articleFileName) ?: false
            is UiState.Paused -> state.articleAudio.isArticleFileName(articleFileName)
            is UiState.Playing -> state.articleAudio.isArticleFileName(articleFileName)
        }
    }

    val isPlayerVisible: Flow<Boolean> = audioPlayerService.uiState.map {
        when (it) {
            UiState.Hidden -> false
            is UiState.Error, is UiState.Loading, is UiState.Paused, is UiState.Playing -> true
        }
    }

    fun setIsVisibleArticle(articleStub: ArticleStub) {
        visibleArticleStub.value = articleStub
    }

    fun clearIsVisibleArticle() {
        visibleArticleStub.value = null
    }

    /**
     * Handle actions on the bottom navigation bar.
     * Will start to play the currently visible article,
     * or dismiss the player if it is was already showing that article.
     */
    fun handleOnAudioActionOnVisibleArticle() {
        viewModelScope.launch {
            val articleStub = visibleArticleStub.value
            if (articleStub != null) {
                val isVisibleArticleActive = isActiveAudio.first()
                if (isVisibleArticleActive) {
                    audioPlayerService.dismissPlayer()
                } else {
                    playArticleStub(articleStub)
                }
            } else {
                log.error("handleOnAudioActionOnVisibleArticle() was called before setIsVisibleArticle()")
                _errorMessageFlow.value = application.getString(R.string.toast_unknown_error)
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageFlow.value = null
    }

    private suspend fun playArticleStub(articleStub: ArticleStub) {
        try {
            audioPlayerService.playArticleAudioAsync(articleStub).await()
        } catch (e: Exception) {
            log.error("Could not play article audio (${articleStub.articleFileName})")
            _errorMessageFlow.value = application.getString(R.string.toast_unknown_error)
        }
    }

    private fun ArticleAudio.isArticleFileName(articleFileName: String?): Boolean =
        article.key == articleFileName

}