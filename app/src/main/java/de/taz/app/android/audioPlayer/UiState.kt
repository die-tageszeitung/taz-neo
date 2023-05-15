package de.taz.app.android.audioPlayer

/**
 * Public player state used to render UI components.
 */
sealed class UiState {
    object Hidden : UiState()
    data class Loading(val articleAudio: ArticleAudio?): UiState()
    data class Playing(val articleAudio: ArticleAudio, val expanded: Boolean): UiState()
    data class Paused(val articleAudio: ArticleAudio, val expanded: Boolean): UiState()

    /**
     * @param wasHandled true if the error has already been presented to a user by any active player
     */
    data class Error(val expanded: Boolean, val wasHandled: Boolean, val articleAudio: ArticleAudio?, val cause: AudioPlayerException?): UiState()
}