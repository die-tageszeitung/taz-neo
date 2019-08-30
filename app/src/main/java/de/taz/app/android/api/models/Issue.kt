package de.taz.app.android.api.models

import de.taz.app.android.api.dto.IssueDto

data class Issue (
    val feedName: String,
    override val date: String,
    override val key: String? = null,
    override val baseUrl: String,
    override val status: IssueStatus,
    override val minResourceVersion: Int,
    override val zipName: String? = null,
    override val zipPdfName: String? = null,
    override val navButton: NavButton? = null,
    override val imprint: Article?,
    override val fileList: List<String>,
    override val fileListPdf: List<String> = listOf(),
    override val sectionList: List<Section>? = null,
    override val pageList: List<Page>? = null
) : IssueDto(
    date, key, baseUrl, status, minResourceVersion, zipName, zipPdfName,
    navButton, imprint, fileList, fileListPdf, sectionList, pageList
) {
    constructor(feedName: String, issueDto: IssueDto): this(
        feedName,
        issueDto.date,
        issueDto.key,
        issueDto.baseUrl,
        issueDto.status,
        issueDto.minResourceVersion,
        issueDto.zipName,
        issueDto.zipPdfName,
        issueDto.navButton,
        issueDto.imprint,
        issueDto.fileList,
        issueDto.fileListPdf ?: listOf(),
        issueDto.sectionList,
        issueDto.pageList
    )
}

enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}