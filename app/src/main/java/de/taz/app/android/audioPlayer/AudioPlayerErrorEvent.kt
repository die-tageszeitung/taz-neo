package de.taz.app.android.audioPlayer

sealed class AudioPlayerErrorEvent(val message: String, val exception: AudioPlayerException?) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioPlayerErrorEvent

        if (message != other.message) return false
        if (exception != other.exception) return false

        return true
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + (exception?.hashCode() ?: 0)
        return result
    }
}

/** Fatal Errors will dismiss the player. */
class AudioPlayerFatalErrorEvent(message: String, exception: AudioPlayerException?) :
    AudioPlayerErrorEvent(message, exception)

/** Info Errors will probably stop the playback, but the player will remain visible. */
class AudioPlayerInfoErrorEvent(message: String, exception: AudioPlayerException?) :
    AudioPlayerErrorEvent(message, exception)
