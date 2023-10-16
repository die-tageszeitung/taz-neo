package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.models.AudioSpeaker

class AudioSpeakerConverter {
    @TypeConverter
    fun toString(audioSpeaker: AudioSpeaker): String {
        return audioSpeaker.name
    }

    @TypeConverter
    fun toAudioSpeakerEnum(value: String): AudioSpeaker {
        return try {
            AudioSpeaker.valueOf(value)
        } catch (e: IllegalArgumentException) {
            AudioSpeaker.UNKNOWN
        }
    }
}