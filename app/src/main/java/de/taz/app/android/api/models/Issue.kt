package de.taz.app.android.api.models

import de.taz.app.android.api.dto.IssueDto

data class Issue (
    val feedName: String,
    val date: String,
    val key: String? = null,
    val baseUrl: String,
    val status: IssueStatus,
    val minResourceVersion: Int,
    val zipName: String? = null,
    val zipPdfName: String? = null,
    val navButton: NavButton? = null,
    val imprint: Article?,
    val fileList: List<String> = emptyList(),
    val fileListPdf: List<String> = emptyList(),
    val sectionList: List<Section> = emptyList(),
    val pageList: List<Page> = emptyList()
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
        issueDto.imprint?.let { Article(it) },
        issueDto.fileList,
        issueDto.fileListPdf ?: emptyList(),
        issueDto.sectionList?.map { Section(it) } ?: emptyList(),
        issueDto.pageList ?: emptyList()
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Issue

        return date == other.date &&
                feedName == other.feedName &&
                key == other.key &&
                baseUrl == other.baseUrl &&
                status == other.status &&
                minResourceVersion == other.minResourceVersion &&
                zipName == other.zipName &&
                zipPdfName == other.zipPdfName &&
                navButton == other.navButton &&
                imprint == other.imprint &&
                fileList.containsAll(other.fileList) &&
                other.fileList.containsAll(fileList) &&
                fileListPdf.containsAll(other.fileListPdf) &&
                other.fileListPdf.containsAll(fileListPdf) &&
                sectionList.containsAll(other.sectionList) &&
                other.sectionList.containsAll(sectionList) &&
                pageList.containsAll(other.pageList) &&
                other.pageList.containsAll(pageList)
    }

    val tag: String
        get() = "$feedName/$date"

}

enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}