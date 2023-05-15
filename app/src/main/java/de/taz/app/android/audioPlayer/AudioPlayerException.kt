package de.taz.app.android.audioPlayer

import android.os.SystemClock

/**
 * AudioPlayerErrors hold a special timestampMs generated from the time they are thrown.
 * This helps to determine if two instances are equal and decided if a message has already be shown
 * to the user.
 */
sealed class AudioPlayerException(
    message: String? = null, cause: Throwable? = null
) : Exception(message, cause) {

    class Generic(message: String? = null, cause: Throwable? = null) :
        AudioPlayerException(message, cause)

    class Network(message: String? = null, cause: Throwable? = null) :
        AudioPlayerException(message, cause)

    // The value of SystemClock.elapsedRealtime() when this exception was created.
    private val timestampMs: Long = SystemClock.elapsedRealtime()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioPlayerException

        if (timestampMs != other.timestampMs) return false
        if (message != other.message) return false
        if (cause?.message != other.cause?.message) return false

        return true
    }

    override fun hashCode(): Int {
        return timestampMs.toInt()
    }
}