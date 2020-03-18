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
    override val minResourceVersion: Int,
    val imprint: Article?,
    val isWeekend: Boolean,
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

    override suspend fun deleteFiles() {
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