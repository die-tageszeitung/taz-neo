package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.persistence.repository.IssueKeyWithPages
import de.taz.app.android.persistence.repository.IssueRepository


import java.util.*

data class IssueWithPages(
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
    override val lastPagePosition: Int?,
    override val lastViewedDate: Date?,
) : AbstractIssue {

    constructor(issue: Issue) : this(
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
        issue.lastPagePosition,
        issue.lastViewedDate,
    )

    override val issueKey: IssueKeyWithPages
        get() = IssueKeyWithPages(feedName, date, status)

    override fun getDownloadDate(applicationContext: Context): Date? {
        return IssueRepository.getInstance(applicationContext).getDownloadDate(this)
    }

    override fun setDownloadDate(date: Date?, applicationContext: Context) {
        IssueRepository.getInstance(applicationContext).apply {
            setDownloadDate(this@IssueWithPages, date)
            get(issueKey)?.let {
                // downloading an issue with pages also means downloading the regular issue
                IssueRepository.getInstance(applicationContext).setDownloadDate(it, date)
            }
        }
    }

    override fun getDownloadTag(): String {
        return "$tag/pdf"
    }
}