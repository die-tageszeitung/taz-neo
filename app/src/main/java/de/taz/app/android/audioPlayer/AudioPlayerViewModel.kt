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
 * Convenience wrapper around the [AudioPlayerService] that may be used from Fragments showing an Article.
 *
 * Note: this ViewModel *must not* be bound to an Activity but shall only be used on Fragments with `by viewModel<AudioPlayerViewModel>()`
 */
abstract class AudioPlayerViewModel(androidApplication: Application) : AndroidViewModel(androidApplication) {
    protected val log by Log

    protected val application = androidApplication as AbstractTazApplication
    protected val audioPlayerService = AudioPlayerService.getInstance(application.applicationContext)

    private val visibleArticleStub: MutableStateFlow<ArticleStub?> = MutableStateFlow(null)
    private val visibleArticleFileName = visibleArticleStub.map { it?.articleFileName }

    private val _errorMessageFlow = MutableStateFlow<String?>(null)
    val errorMessageFlow: StateFlow<String?> = _errorMessageFlow.asStateFlow()

    val isPlayerVisible = audioPlayerService.uiState.map { it.isPlayerVisible() }

    /**
     * True if the currently visible article is active within the audio player.
     * Note, that does not mean it is playing.
     */
    val isActiveAudio: Flow<Boolean> = combine(
        visibleArticleFileName, isPlayerVisible, audioPlayerService.currentItem
    ) { articleFileName: String?, isPlayerVisible: Boolean, audioPlayerItem: AudioPlayerItem? ->
        val currentlyPlayingArticle = when(audioPlayerItem) {
            is ArticleAudio -> audioPlayerItem.article
            is IssueAudio -> audioPlayerItem.currentArticle
            is PodcastAudio, null -> null
        }

        isPlayerVisible && currentlyPlayingArticle != null && currentlyPlayingArticle.key == articleFileName
    }

    fun setVisibleArticle(articleStub: ArticleStub) {
        visibleArticleStub.value = articleStub
    }

    fun clearVisibleArticle() {
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
                    try {
                        play(articleStub)
                    } catch (e: Exception) {
                        log.error("Could not play article audio (${articleStub.articleFileName})", e)
                        _errorMessageFlow.value = application.getString(R.string.toast_unknown_error)
                    }
                }
            } else {
                log.error("handleOnAudioActionOnVisibleArticle() was called before setVisibleArticle()")
                _errorMessageFlow.value = application.getString(R.string.toast_unknown_error)
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageFlow.value = null
    }

    abstract suspend fun play(articleStub: ArticleStub)
}


class ArticleAudioPlayerViewModel(androidApplication: Application) :
    AudioPlayerViewModel(androidApplication) {

    override suspend fun play(articleStub: ArticleStub) {
        audioPlayerService.playArticleAudioAsync(articleStub).await()
    }
}

class IssueAudioPlayerViewModel(androidApplication: Application) :
    AudioPlayerViewModel(androidApplication) {

    override suspend fun play(articleStub: ArticleStub) {
        val issueStub = requireNotNull(articleStub.getIssueStub(application.applicationContext))
        audioPlayerService.playIssueAsync(issueStub, articleStub).await()
    }

}