package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.SectionDto
import de.taz.app.android.api.models.ArticleType
import de.taz.app.android.api.models.Section
import de.taz.app.android.persistence.repository.IssueKey

object SectionMapper {
    fun from(issueKey: IssueKey, sectionDto: SectionDto): Section {
        return Section(
            sectionHtml = FileEntryMapper.from(issueKey, sectionDto.sectionHtml),
            issueDate = issueKey.date,
            title = sectionDto.title,
            type = SectionTypeMapper.from(sectionDto.type),
            navButton = ImageMapper.from(issueKey, sectionDto.navButton),
            articleList = sectionDto.articleList?.map {
                ArticleMapper.from(issueKey, it, ArticleType.STANDARD)
            } ?: listOf(),
            imageList = sectionDto.imageList?.map { ImageMapper.from(issueKey, it) } ?: listOf(),
            extendedTitle = sectionDto.extendedTitle,
            dateDownload = null,
            podcast = sectionDto.podcast?.let { AudioMapper.from(issueKey, it) }
        )
    }
}