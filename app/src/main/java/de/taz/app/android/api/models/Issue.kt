package de.taz.app.android.api.models

import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.persistence.repository.DownloadRepository

data class Issue(
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

    val tag: String
        get() = "$feedName/$date"

    val issueFileList: List<String>
        get() = fileList.filter { !it.startsWith("/global/") }

    val globalFileList: List<String>
        get() = fileList.filter { it.startsWith("/global/") }.map { it.split("/").last() }

    fun isDownloaded(): Boolean {
        DownloadRepository.getInstance().let { downloadRepository ->
            return downloadRepository.isDownloaded(issueFileList) &&
                    downloadRepository.isDownloaded(globalFileList)
        }
    }

}

enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}