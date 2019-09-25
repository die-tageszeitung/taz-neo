package de.taz.app.android.api.models

import androidx.room.Entity
import de.taz.app.android.api.interfaces.IssueFunctions
import de.taz.app.android.persistence.repository.IssueRepository

@Entity(
    tableName = "Issue",
    primaryKeys = ["feedName", "date"]
)
data class IssueBase(
    override val feedName: String,
    override val date: String,
    val key: String? = null,
    val baseUrl: String,
    val status: IssueStatus,
    val minResourceVersion: Int,
    val zipName: String? = null,
    val zipPdfName: String? = null,
    val navButton: NavButton? = null,
    override val fileList: List<String>,
    val fileListPdf: List<String> = emptyList()
): IssueFunctions {

    constructor(issue: Issue): this (
        issue.feedName, issue.date, issue.key, issue.baseUrl, issue.status,
        issue.minResourceVersion, issue.zipName, issue.zipPdfName,
        issue.navButton, issue.fileList, issue.fileListPdf
    )

    fun getIssue(): Issue {
        return IssueRepository.getInstance().getIssue(this)
    }
}
