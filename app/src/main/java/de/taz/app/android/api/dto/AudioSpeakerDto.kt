package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = AudioSpeakerDtoEnumSerializer::class)
enum class AudioSpeakerDto {
    human,
    machineMale,
    machineFemale,
    podcast,
    UNKNOWN,
}

object AudioSpeakerDtoEnumSerializer :
    EnumSerializer<AudioSpeakerDto>(AudioSpeakerDto.values(), AudioSpeakerDto.UNKNOWN)