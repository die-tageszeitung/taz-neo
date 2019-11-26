package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.download.DownloadService
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
        issueDto.imprint?.let { Article(issueDto.date, it, ArticleType.IMPRINT) },
        issueDto.fileList,
        issueDto.fileListPdf ?: emptyList(),
        issueDto.sectionList?.map { Section(issueDto.date, it) } ?: emptyList(),
        issueDto.pageList ?: emptyList()
    )

    override fun getAllFiles(): List<FileEntry> {
        val files = mutableListOf(moment.imageList)
        imprint?.let {
            files.add(imprint.getAllFiles())
        }
        files.addAll(sectionList.map { it.getAllFiles() })
        return files.flatten().distinct()
    }

    override fun getDownloadTag(): String? {
        return tag
    }

    suspend fun downloadMoment(applicationContext: Context) {
        withContext(Dispatchers.IO) {
            DownloadService.download(applicationContext, moment)
        }
    }

    suspend fun downloadPages(applicationContext: Context) {
        withContext(Dispatchers.IO) {
            pageList.forEach {
                DownloadService.download(applicationContext, it)
            }
        }
    }

    override fun getIssueOperations(): IssueOperations? {
        return this
    }
}

enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}

enum class ArticleType {
   STANDARD, IMPRINT;
}