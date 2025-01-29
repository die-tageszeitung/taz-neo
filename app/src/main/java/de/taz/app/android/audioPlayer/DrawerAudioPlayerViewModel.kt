package de.taz.app.android.audioPlayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.taz.app.android.AbstractTazApplication
import de.taz.app.android.R
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Convenience wrapper around the [AudioPlayerService] that may be used from Drawer Fragments to play an Issue.
 *
 * Note: this ViewModel *must not* be bound to an Activity but shall only be used on Fragments with `by viewModel<DrawerAudioPlayerViewModel>()`
 */
class DrawerAudioPlayerViewModel(androidApplication: Application) :
    AndroidViewModel(androidApplication) {
    private val log by Log

    private val application = androidApplication as AbstractTazApplication
    private val audioPlayerService = AudioPlayerService.getInstance(application.applicationContext)
    private val tracker = Tracker.getInstance(application.applicationContext)

    private val issueStub: MutableStateFlow<IssueStub?> = MutableStateFlow(null)
    private val issueKey = issueStub.map { it?.issueKey }

    private val _errorMessageFlow = MutableStateFlow<String?>(null)
    val errorMessageFlow: StateFlow<String?> = _errorMessageFlow.asStateFlow()

    private val currentAudioPlayerItemIssueKey = audioPlayerService.currentItem.map {
        it?.issueKey
    }

    val isIssueActiveAudio: Flow<Boolean> =
        combine(issueKey, audioPlayerService.uiState, currentAudioPlayerItemIssueKey) { issueKey: IssueKey?, state: UiState, currentAudioPlayerItemIssueKey: AbstractIssueKey? ->
            when (state) {
                UiState.Hidden -> false
                is UiState.MaxiPlayer -> isActiveIssueAudio(state.playerState, issueKey, currentAudioPlayerItemIssueKey)
                is UiState.MiniPlayer ->  isActiveIssueAudio(state.playerState, issueKey, currentAudioPlayerItemIssueKey)
                is UiState.Playlist -> isActiveIssueAudio(state.playerState, issueKey, currentAudioPlayerItemIssueKey)
            }
        }

    private fun isActiveIssueAudio(playerState: UiState.PlayerState, issueKey: IssueKey?, currentAudioPlayerItemIssueKey: AbstractIssueKey?): Boolean = when(playerState) {
        UiState.PlayerState.Initializing, is UiState.PlayerState.Paused -> false
        // Only show the drawers audio indicator as playing if isAutoPlayNext is enabled and if a whole issue is playing
        // FIXME (johannes): checking state.playerState.controls.autoPlayNext is a hack that allows me to skip passing additional information about the "type" of playing thing to the UI state for now
        is UiState.PlayerState.Playing -> playerState.playerUiState.isAutoPlayNext
                && playerState.playerUiState.controls.autoPlayNext == UiState.ControlValue.ENABLED
                && currentAudioPlayerItemIssueKey != null
                && IssueKey(currentAudioPlayerItemIssueKey) == issueKey
    }

    fun setIssueStub(issueStub: IssueStub) {
        this.issueStub.value = issueStub
    }

    fun handleOnPlayAllClicked() {
        tracker.trackDrawerTapPlayIssueEvent()
        val currentIssueStub = issueStub.value
        if (currentIssueStub != null) {
            try {
                audioPlayerService.playIssue(currentIssueStub)
            } catch (e: Exception) {
                log.error("Could not play issue audio (${currentIssueStub.issueKey})", e)
                _errorMessageFlow.value = application.getString(R.string.toast_unknown_error)
            }
        } else {
            log.error("handleOnPlayAllClicked() was called before setIssue()")
            _errorMessageFlow.value = application.getString(R.string.toast_unknown_error)
        }
    }

    fun enqueue(articleKey: String) {
        try {
            audioPlayerService.playArticle(
                articleKey,
                replacePlaylist = false,
                playImmediately = false
            )
        } catch (e: Exception) {
            log.error("Could not play article audio (${articleKey})", e)
            _errorMessageFlow.value = application.getString(R.string.toast_unknown_error)}
    }

    fun removeFromPlaylist(articleKey: String) {
        val audioToRemove =
            audioPlayerService.playlistState.value.items.find { it.playableKey == articleKey }
        audioToRemove?.let {
            audioPlayerService.removeItem(it)
        }
    }

    fun clearErrorMessage() {
        _errorMessageFlow.value = null
    }

    fun showPlaylist() {
        audioPlayerService.showPlaylist()
    }
}