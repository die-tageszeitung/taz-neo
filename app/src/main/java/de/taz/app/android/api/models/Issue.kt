package de.taz.app.android.api.models

import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.interfaces.IssueOperations

data class Issue(
    override val feedName: String,
    override val date: String,
    val key: String? = null,
    val baseUrl: String,
    val status: IssueStatus,
    val minResourceVersion: Int,
    val zipName: String? = null,
    val zipPdfName: String? = null,
    val navButton: NavButton? = null,
    val imprint: Article?,
    override val fileList: List<String> = emptyList(),
    val fileListPdf: List<String> = emptyList(),
    val sectionList: List<Section> = emptyList(),
    val pageList: List<Page> = emptyList()
) : IssueOperations {
    constructor(feedName: String, issueDto: IssueDto) : this(
        feedName,
        issueDto.date,
        issueDto.key,
        issueDto.baseUrl,
        issueDto.status,
        issueDto.minResourceVersion,
        issueDto.zipName,
        issueDto.zipPdfName,
        issueDto.navButton,
        issueDto.imprint?.let { Article(it) },
        issueDto.fileList,
        issueDto.fileListPdf ?: emptyList(),
        issueDto.sectionList?.map { Section(it) } ?: emptyList(),
        issueDto.pageList ?: emptyList()
    )

}

enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}