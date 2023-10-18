package de.taz.app.android.audioPlayer

import android.net.Uri

/**
 * Public player state used to render UI components.
 */
sealed class UiState {
    object Hidden : UiState()
    data class Initializing(val item: Item) : UiState()
    data class InitError(
        val wasHandled: Boolean,
        val cause: AudioPlayerException
    ) : UiState()

    data class Playing(
        val playerState: PlayerState
    ) : UiState()

    data class Paused(
        val playerState: PlayerState
    ) : UiState()

    /**
     * @param wasHandled true if the error has already been presented to a user by any active player
     */
    data class Error(
        val wasHandled: Boolean,
        val playerState: PlayerState,
        val cause: AudioPlayerException
    ) : UiState()

    // Helper functions
    fun isExpanded(): Boolean = when (this) {
        is Error -> playerState.expanded
        Hidden -> false
        is Initializing -> false
        is InitError -> false
        is Paused -> playerState.expanded
        is Playing -> playerState.expanded
    }

    fun isPlayerVisible(): Boolean = when (this) {
        Hidden, is InitError -> false
        is Error, is Initializing, is Paused, is Playing -> true
    }

    fun copyWithExpanded(isExpanded: Boolean): UiState = when (this) {
        is Error -> copy(playerState = playerState.copy(expanded = isExpanded))
        is Paused -> copy(playerState = playerState.copy(expanded = isExpanded))
        is Playing -> copy(playerState = playerState.copy(expanded = isExpanded))
        Hidden, is Initializing, is InitError -> this
    }

    // Helper classes
    data class PlayerState(
        val item: Item,
        val expanded: Boolean,
        val playbackSpeed: Float,
        val isAutoPlayNext: Boolean,
        val controls: Controls,
    )

    data class Item(
        val title: String,
        val author: String?,
        val coverImageUri: Uri?,
    )

    data class Controls(
        val skipNext: ControlValue,
        val skipPrevious: ControlValue,
        val autoPlayNext: ControlValue
    )

    enum class ControlValue {
        ENABLED,
        DISABLED,
        HIDDEN,
    }
}
