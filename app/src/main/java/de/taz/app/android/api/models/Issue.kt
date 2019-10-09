package de.taz.app.android.api.models

import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Issue(
    override val feedName: String,
    override val date: String,
    val moment: Moment,
    val key: String? = null,
    override val baseUrl: String,
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
) : IssueOperations, CacheableDownload {

    private val log by Log

    constructor(feedName: String, issueDto: IssueDto) : this(
        feedName,
        issueDto.date,
        Moment(issueDto.moment),
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

    override fun getAllFiles(): List<FileEntry> {
        val files = mutableListOf(moment.imageList)
        imprint?.let {
            files.add(imprint.getAllFiles())
        }
        files.addAll(sectionList.map { it.getAllFiles() })
        files.addAll(pageList.map { it.getAllFiles() })
        log.debug("issue $tag has ${files.flatten().size} files")
        return files.flatten()
    }

    override fun getDownloadTag(): String? {
        return tag
    }

}

enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}