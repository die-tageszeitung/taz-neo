package de.taz.app.android.api.models

import de.taz.app.android.persistence.repository.IssueKey

import java.util.*

data class Issue(
    override val feedName: String,
    override val date: String,
    override val validityDate: String?,
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
    override val lastPagePosition: Int?,
    override val lastViewedDate: Date?,
) : AbstractIssue {

    constructor(issue: IssueWithPages) : this(
        issue.feedName,
        issue.date,
        issue.validityDate,
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
        issue.lastPagePosition,
        issue.lastViewedDate,
    )

    override val issueKey: IssueKey
        get() = IssueKey(feedName, date, status)

    /**
     * Copy this issue with the updated metadata from the provided issue stub.
     */
    fun copyWithMetadata(issueStub: IssueStub): Issue {
        require(issueKey == issueStub.issueKey) { "Metadata may only be updated for the same issue" }
        return copy(
            feedName = issueStub.feedName,
            date = issueStub.date,
            key = issueStub.key,
            baseUrl = issueStub.baseUrl,
            status = issueStub.status,
            minResourceVersion = issueStub.minResourceVersion,
            isWeekend = issueStub.isWeekend,
            moTime = issueStub.moTime,
            dateDownload = issueStub.dateDownload,
            dateDownloadWithPages = issueStub.dateDownloadWithPages,
            lastDisplayableName = issueStub.lastDisplayableName,
            lastPagePosition = issueStub.lastPagePosition,
            lastViewedDate = issueStub.lastViewedDate
        )
    }
}


/**
 * The issue status should be in a ascending ordinal for they "value"
 * So the regular issue is more favorable than the public.
 */
enum class IssueStatus {
    public,
    demo,
    regular,
    locked,
}
