package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.AudioDto
import de.taz.app.android.api.models.Audio
import de.taz.app.android.persistence.repository.IssueKey

object AudioMapper {
    fun from(issueKey: IssueKey, audioDto: AudioDto): Audio? {
        if (audioDto.file == null) {
            return null
        }

        return Audio(
            FileEntryMapper.from(issueKey, audioDto.file),
            audioDto.playtime,
            audioDto.duration,
            AudioSpeakerMapper.from(audioDto.speaker),
            audioDto.breaks?.asList()
        )
    }
}

