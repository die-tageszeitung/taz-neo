package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "Issue",
    primaryKeys = ["feedName", "date"]
)
data class IssueBase(
    val feedName: String,
    val date: String,
    val key: String? = null,
    val baseUrl: String,
    val status: IssueStatus,
    val minResourceVersion: Int,
    val zipName: String? = null,
    val zipPdfName: String? = null,
    //val navButton: NavButton? = null,
    //val imprint: Article,
    val fileList: List<String>,
    val fileListPdf: List<String>
    //val sectionList: List<Section>? = null,
    //val pageList: List<Page>? = null
) {

    constructor(issue: Issue): this (
        issue.feedName, issue.date, issue.key, issue.baseUrl, issue.status,
        issue.minResourceVersion, issue.zipName, issue.zipPdfName, issue.fileList, issue.fileListPdf
    )
}
