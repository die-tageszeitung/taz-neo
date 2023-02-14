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
