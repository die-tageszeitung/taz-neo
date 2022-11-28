package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.models.ArticleType
import de.taz.app.android.api.models.Issue
import de.taz.app.android.persistence.repository.IssueKey

object IssueMapper {
    fun from(feedName: String, issueDto: IssueDto): Issue {
        val status = IssueStatusMapper.from(issueDto.status)
        val issueKey = IssueKey(feedName, issueDto.date, status)

        return Issue(
            feedName,
            issueDto.date,
            issueDto.validityDate,
            MomentMapper.from(issueKey, issueDto.baseUrl, issueDto.moment),
            issueDto.key,
            issueDto.baseUrl,
            status,
            issueDto.minResourceVersion,
            issueDto.imprint?.let { ArticleMapper.from(issueKey, it, ArticleType.IMPRINT) },
            issueDto.isWeekend,
            issueDto.sectionList?.map { SectionMapper.from(issueKey, it) } ?: emptyList(),
            issueDto.pageList
                ?.map { PageMapper.from(issueKey, issueDto.baseUrl, it) }
                ?: emptyList(),
            issueDto.moTime,
            null,
            null,
            null,
            null,
            null
        )
    }
}