package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.AudioSpeakerDto
import de.taz.app.android.api.models.AudioSpeaker

object AudioSpeakerMapper {
    fun from(audioSpeakerDto: AudioSpeakerDto?): AudioSpeaker = when (audioSpeakerDto) {
        AudioSpeakerDto.human -> AudioSpeaker.HUMAN
        AudioSpeakerDto.machineMale -> AudioSpeaker.MACHINE_MALE
        AudioSpeakerDto.machineFemale -> AudioSpeaker.MACHINE_FEMALE
        AudioSpeakerDto.podcast -> AudioSpeaker.PODCAST
        AudioSpeakerDto.UNKNOWN, null -> AudioSpeaker.UNKNOWN
    }
}