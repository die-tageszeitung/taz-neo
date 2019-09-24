package de.taz.app.android.api.models

import de.taz.app.android.api.dto.IssueDto

data class Issue(
    override val feedName: String,
    override val date: String,
    override val key: String? = null,
    override val baseUrl: String,
    override val status: IssueStatus,
    override val minResourceVersion: Int,
    override val zipName: String? = null,
    override val zipPdfName: String? = null,
    override val navButton: NavButton? = null,
    val imprint: Article?,
    override val fileList: List<String> = emptyList(),
    override val fileListPdf: List<String> = emptyList(),
    val sectionList: List<Section> = emptyList(),
    val pageList: List<Page> = emptyList()
) : IssueBase(
    feedName,
    date,
    key,
    baseUrl,
    status,
    minResourceVersion,
    zipName,
    zipPdfName,
    navButton,
    fileList,
    fileListPdf
) {
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