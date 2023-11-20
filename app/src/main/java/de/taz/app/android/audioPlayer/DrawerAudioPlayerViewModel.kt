package de.taz.app.android.audioPlayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch

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
        when (it) {
            is ArticleAudio -> it.issueStub.issueKey
            is IssueAudio -> it.issueStub.issueKey
            is PodcastAudio -> it.issueStub.issueKey
            null -> null
        }
    }

    val isIssueActiveAudio: Flow<Boolean> =
        combine(issueKey, audioPlayerService.uiState, currentAudioPlayerItemIssueKey) { issueKey: IssueKey?, state: UiState, currentAudioPlayerItemIssueKey: AbstractIssueKey? ->
            when (state) {
                UiState.Hidden, is UiState.InitError, is UiState.Initializing, is UiState.Paused, is UiState.Error -> false
                // Only show the drawers audio indicator as playing if isAutoPlayNext is enabled and if a whole issue is playing
                // FIXME (johannes): checking state.playerState.controls.autoPlayNext is a hack that allows me to skip passing additional information about the "type" of playing thing to the UI state for now
                is UiState.Playing -> state.playerState.isAutoPlayNext
                        && state.playerState.controls.autoPlayNext == UiState.ControlValue.ENABLED
                        && currentAudioPlayerItemIssueKey != null
                        && IssueKey(currentAudioPlayerItemIssueKey) == issueKey
            }
        }

    fun setIssueStub(issueStub: IssueStub) {
        this.issueStub.value = issueStub
    }

    fun handleOnPlayAllClicked() {
        tracker.trackDrawerTapPlayIssueEvent()
        viewModelScope.launch {
            val currentIssueStub = issueStub.value
            if (currentIssueStub != null) {
                try {
                    audioPlayerService.setAutoPlayNext(true)
                    audioPlayerService.playIssueAsync(currentIssueStub, null).await()
                } catch (e: Exception) {
                    log.error("Could not play issue audio (${currentIssueStub.issueKey})", e)
                    _errorMessageFlow.value = application.getString(R.string.toast_unknown_error)
                }
            } else {
                log.error("handleOnPlayAllClicked() was called before setIssue()")
                _errorMessageFlow.value = application.getString(R.string.toast_unknown_error)
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageFlow.value = null
    }
}