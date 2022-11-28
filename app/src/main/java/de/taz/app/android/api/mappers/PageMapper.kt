package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.PageDto
import de.taz.app.android.api.models.Page
import de.taz.app.android.persistence.repository.IssueKey

object PageMapper {
    fun from(issueKey: IssueKey, baseUrl: String, pageDto: PageDto): Page {
        return Page(
            FileEntryMapper.from(issueKey, pageDto.pagePdf),
            pageDto.title,
            pageDto.pagina,
            pageDto.type?.let { PageTypeMapper.from(it) },
            pageDto.frameList?.map { FrameMapper.from(it) },
            null,
            baseUrl
        )
    }
}