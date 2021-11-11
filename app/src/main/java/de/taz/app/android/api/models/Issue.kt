package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueKeyWithPages

import java.util.*

data class Issue(
    override val feedName: String,
    override val date: String,
    val moment: Moment,
    override val key: String? = null,
    override val baseUrl: String,
    override val status: IssueStatus,
    override val minResourceVersion: Int,
    override val imprint: Article?,
    override val isWeekend: Boolean,
    override val sectionList: List<Section> = emptyList(),
    override val pageList: List<Page> = emptyList(),
    override val moTime: String,
    override val dateDownload: Date?,
    override val dateDownloadWithPages: Date?,
    override val lastDisplayableName: String?,
    override val lastPagePosition: Int?
) : AbstractIssue {

    constructor(feedName: String, issueDto: IssueDto) : this(
        feedName,
        issueDto.date,
        Moment(IssueKey(feedName, issueDto.date, issueDto.status), issueDto.baseUrl, issueDto.moment),
        issueDto.key,
        issueDto.baseUrl,
        issueDto.status,
        issueDto.minResourceVersion,
        issueDto.imprint?.let { Article(IssueKey(feedName, issueDto.date, issueDto.status), it, ArticleType.IMPRINT) },
        issueDto.isWeekend,
        issueDto.sectionList?.map { Section(IssueKey(feedName, issueDto.date, issueDto.status), it) } ?: emptyList(),
        issueDto.pageList?.map { Page(IssueKey(feedName, issueDto.date, issueDto.status), it, issueDto.baseUrl) } ?: emptyList(),
        issueDto.moTime,
        null,
        null,
        null,
        null
    )


    constructor(issue: IssueWithPages) : this(
        issue.feedName,
        issue.date,
        issue.moment,
        issue.key,
        issue.baseUrl,
        issue.status,
        issue.minResourceVersion,
        issue.imprint,
        issue.isWeekend,
        issue.sectionList,
        issue.pageList,
        issue.moTime,
        issue.dateDownload,
        issue.dateDownloadWithPages,
        issue.lastDisplayableName,
        issue.lastPagePosition
    )

    override val issueKey: IssueKey
        get() = IssueKey(feedName, date, status)
}


/**
 * The issue status should be in a ascending ordinal for they "value"
 * So the regular issue is more favorable than the public.
 */
@JsonClass(generateAdapter = false)
enum class IssueStatus {
    public,
    regular
}
