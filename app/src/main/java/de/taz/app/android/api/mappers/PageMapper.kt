package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.PageDto
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageStub
import de.taz.app.android.persistence.repository.IssueKey

object PageMapper {
    fun from(issueKey: IssueKey, baseUrl: String, pageDto: PageDto): Page {
        return Page(
            PageStub(
                pageDto.pagePdf.name,
                pageDto.title,
                pageDto.pagina,
                pageDto.type?.let { PageTypeMapper.from(it) },
                pageDto.frameList?.map { FrameMapper.from(it) },
                baseUrl,
                pageDto.podcast?.file?.name,
                pageDto.adIdList,
            ),
            pdfFile = FileEntryMapper.from(issueKey, pageDto.pagePdf),
            audioFile = AudioWithFileMapper.from(issueKey, pageDto.podcast)
        )
    }
}