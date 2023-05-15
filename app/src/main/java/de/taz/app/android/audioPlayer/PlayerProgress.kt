package de.taz.app.android.audioPlayer

/**
 * Holds the current position and the total duration in milliseconds of the currently active audio.
 */
data class PlayerProgress(val currentMs: Long, val totalMs: Long)