package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.MomentDto
import de.taz.app.android.api.models.Moment
import de.taz.app.android.persistence.repository.IssueKey

object MomentMapper {
    fun from(issueKey: IssueKey, baseUrl: String, momentDto: MomentDto): Moment {
        return Moment(
            issueKey.feedName,
            issueKey.date,
            issueKey.status,
            baseUrl,
            momentDto.imageList
                ?.map { ImageMapper.from(issueKey, it) }
                ?: emptyList(),
            momentDto.creditList
                ?.map { ImageMapper.from(issueKey, it) }
                ?: emptyList(),
            momentDto.momentList
                ?.map { FileEntryMapper.from(issueKey, it) }
                ?: emptyList(),
            null
        )
    }
}