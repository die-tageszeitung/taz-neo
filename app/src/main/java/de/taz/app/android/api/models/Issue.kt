package de.taz.app.android.api.models

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
    override val minResourceVersion: Int,
    val imprint: Article?,
    override val isWeekend: Boolean,
    val sectionList: List<Section> = emptyList(),
    val pageList: List<Page> = emptyList(),
    override val dateDownload: Date? = null,
    override val downloadedField: Boolean? = false
) : IssueOperations, CacheableDownload {

    constructor(feedName: String, issueDto: IssueDto) : this(
        feedName,
        issueDto.date,
        Moment(feedName, issueDto.date, issueDto.moment),
        issueDto.key,
        issueDto.baseUrl,
        issueDto.status,
        issueDto.minResourceVersion,
        issueDto.imprint?.let { Article(feedName, issueDto.date, it, ArticleType.IMPRINT) },
        issueDto.isWeekend,
        issueDto.sectionList?.map { Section(feedName, issueDto.date, it) } ?: emptyList(),
        issueDto.pageList?.map { Page(feedName, issueDto.date, it) } ?: emptyList()
    )

    override fun getAllFileNames(): List<String> {
        val files = mutableListOf(moment.getAllFileNames())
        imprint?.let {
            files.add(imprint.getAllFileNames())
        }
        sectionList.forEach { section ->
            files.add(section.getAllFileNames())
            getArticleList().forEach { article ->
                files.add(article.getAllFileNames())
            }
        }
        return files.flatten().distinct()
    }

    override fun getDownloadTag(): String? {
        return tag
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

    override suspend fun deleteFiles() {
        this.setIsDownloaded(false)
        val allFiles = getAllFiles()
        val bookmarkedArticleFiles = sectionList.fold(mutableListOf<String>(), { acc, section ->
            acc.addAll(
                section.articleList.filter { it.bookmarked }.map { it.getAllFileNames() }.flatten()
                    .distinct()
            )
            acc
        })
        allFiles.filter { it.name !in bookmarkedArticleFiles }.forEach { it.deleteFile() }
        IssueRepository.getInstance().resetDownloadDate(this)
    }

    suspend fun delete() = withContext(Dispatchers.Default) {
        DownloadService.getInstance().cancelAllDownloads()
        moment.deleteFiles()
        deleteFiles()
        IssueRepository.getInstance().delete(this@Issue)
    }

    override fun setIsDownloaded(downloaded: Boolean) {
        IssueRepository.getInstance().update(IssueStub(this).copy(downloadedField = downloaded))
        sectionList.forEach { section ->
            section.setIsDownloaded(downloaded)
            section.articleList.forEach { article ->
                article.setIsDownloaded(downloaded)
            }
        }
        imprint?.setIsDownloaded(downloaded)
        pageList.forEach { it.setIsDownloaded(downloaded) }
        moment.setIsDownloaded(downloaded)
    }

}

@JsonClass(generateAdapter = false)
enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}

