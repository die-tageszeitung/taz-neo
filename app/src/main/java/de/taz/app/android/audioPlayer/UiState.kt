package de.taz.app.android.audioPlayer

/**
 * Public player state used to render UI components.
 */
sealed class UiState {
    data object Hidden : UiState()
    data class MiniPlayer(val playerState: PlayerState) : UiState()
    data class MaxiPlayer(val playerState: PlayerState) : UiState()

    sealed class PlayerState {
        data object Initializing : PlayerState()

        data class Playing(
            val playerUiState: PlayerUiState
        ) : PlayerState()

        data class Paused(
            val playerUiState: PlayerUiState
        ) : PlayerState()
    }

    // Helper functions
    fun getPlayerStateOrNull(): PlayerState? = when (this) {
        Hidden -> null
        is MaxiPlayer -> playerState
        is MiniPlayer -> playerState
    }

    fun isPlayerVisible(): Boolean = when (this) {
        Hidden -> false
        is MaxiPlayer, is MiniPlayer -> true
    }

    fun copyWithPlayerState(
        playerState: PlayerState,
        isFirstAudioPlayEver: Boolean = false,
    ): UiState = when (this) {
        Hidden -> {
            // Determine the initial state the player is in when shown first.
            // The very first time the MaxiPlayer should be shown.
            if (isFirstAudioPlayEver) {
                MaxiPlayer(playerState)
            } else {
                MiniPlayer(playerState)
            }
        }
        is MaxiPlayer -> MaxiPlayer(playerState)
        is MiniPlayer -> MiniPlayer(playerState)
    }

    // Helper classes
    data class PlayerUiState(
        val uiItem: AudioPlayerItem.UiItem,
        val playbackSpeed: Float,
        val isAutoPlayNext: Boolean,
        val controls: Controls,
        val isLoading: Boolean,
    )

    data class Controls(
        val skipNext: ControlValue,
        val skipPrevious: ControlValue,
        val autoPlayNext: ControlValue,
        val seekBreaks: Boolean,
    )

    enum class ControlValue {
        ENABLED,
        DISABLED,
        HIDDEN,
    }
}
