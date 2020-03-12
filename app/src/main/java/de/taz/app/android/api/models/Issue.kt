package de.taz.app.android.api.models

import android.content.Context
import com.squareup.moshi.JsonClass
import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

data class Issue(
    override val feedName: String,
    override val date: String,
    val moment: Moment,
    val key: String? = null,
    override val baseUrl: String,
    override val status: IssueStatus,
    val minResourceVersion: Int,
    val zipName: String? = null,
    val zipPdfName: String? = null,
    val navButton: NavButton? = null,
    val imprint: Article?,
    val fileList: List<String> = emptyList(),
    val fileListPdf: List<String> = emptyList(),
    val sectionList: List<Section> = emptyList(),
    val pageList: List<Page> = emptyList(),
    override val dateDownload: Date? = null
) : IssueOperations, CacheableDownload {

    constructor(feedName: String, issueDto: IssueDto) : this(
        feedName,
        issueDto.date,
        Moment(feedName, issueDto.date, issueDto.moment),
        issueDto.key,
        issueDto.baseUrl,
        issueDto.status,
        issueDto.minResourceVersion,
        issueDto.zipName,
        issueDto.zipPdfName,
        issueDto.navButton,
        issueDto.imprint?.let { Article(feedName, issueDto.date, it, ArticleType.IMPRINT) },
        issueDto.fileList,
        issueDto.fileListPdf ?: emptyList(),
        issueDto.sectionList?.map { Section(feedName, issueDto.date, it) } ?: emptyList(),
        issueDto.pageList?.map { Page(feedName, issueDto.date, it) } ?: emptyList()
    )

    override fun getAllFiles(): List<FileEntry> {
        val files = mutableListOf(moment.getAllFiles())
        imprint?.let {
            files.add(imprint.getAllFiles())
        }
        sectionList.forEach { section ->
            files.add(section.getAllFiles())
            getArticleList().forEach { article ->
                files.add(article.getAllFiles())
            }
        }
        return files.flatten().distinct()
    }

    override fun getDownloadTag(): String? {
        return tag
    }

    suspend fun downloadMoment(applicationContext: Context? = null) {
        withContext(Dispatchers.IO) {
            DownloadService.getInstance(applicationContext).download(moment)
        }
    }

    suspend fun downloadPages(applicationContext: Context? = null) {
        withContext(Dispatchers.IO) {
            pageList.forEach {
                DownloadService.getInstance(applicationContext).download(it)
            }
        }
    }

    override fun getIssueOperations(): IssueOperations? {
        return this
    }

    fun getArticleList(): List<Article> {
        val articleList = mutableListOf<Article>()
        sectionList.forEach {
            articleList.addAll(it.articleList)
        }
        return articleList
    }

    override fun deleteFiles() {
        super.deleteFiles()
        IssueRepository.getInstance().resetDownloadDate(this)
    }

    suspend fun delete() = withContext(Dispatchers.Default) {
        DownloadService.getInstance().cancelAllDownloads()
        moment.deleteFiles()
        deleteFiles()
        IssueRepository.getInstance().delete(this@Issue)
    }

}
@JsonClass(generateAdapter = false)
enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}

enum class ArticleType {
    STANDARD, IMPRINT;
}