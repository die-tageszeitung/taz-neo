package de.taz.app.android.audioPlayer

/**
 * Public player state used to render UI components.
 */
sealed class UiState {
    data object Hidden : UiState()
    data class MiniPlayer(val playerState: PlayerState) : UiState()
    data class MaxiPlayer(val playerState: PlayerState) : UiState()
    data class Playlist(
        val playlist: de.taz.app.android.audioPlayer.Playlist,
        val playerState: PlayerState,
    ) : UiState()

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
        is Playlist -> playerState
    }

    fun isPlayerVisible(): Boolean = when (this) {
        Hidden -> false
        is MaxiPlayer, is MiniPlayer, is Playlist -> true
    }

    fun copyWithPlayerState(playerState: PlayerState): UiState = when (this) {
        // FIXME: this is not a good place to define these.. they are very weird to find here
        Hidden -> MaxiPlayer(playerState) // initial state the player is in when shown first
        is MaxiPlayer -> MaxiPlayer(playerState)
        is MiniPlayer -> MiniPlayer(playerState)
        is Playlist -> Playlist(playlist, playerState)
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
