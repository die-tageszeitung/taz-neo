package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.persistence.repository.IssueKey

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
    override val moTime: String,
    override val dateDownload: Date?,
    override val lastDisplayableName: String?
) : IssueOperations, DownloadableCollection {

    constructor(feedName: String, issueDto: IssueDto) : this(
        feedName,
        issueDto.date,
        Moment(IssueKey(feedName, issueDto.date, issueDto.status), issueDto.baseUrl, issueDto.moment),
        issueDto.key,
        issueDto.baseUrl,
        issueDto.status,
        issueDto.minResourceVersion,
        issueDto.imprint?.let { Article(IssueKey(feedName, issueDto.date, issueDto.status), it, ArticleType.IMPRINT) },
        issueDto.isWeekend,
        issueDto.sectionList?.map { Section(IssueKey(feedName, issueDto.date, issueDto.status), it) } ?: emptyList(),
        issueDto.pageList?.map { Page(IssueKey(feedName, issueDto.date, issueDto.status), it, issueDto.baseUrl) } ?: emptyList(),
        issueDto.moTime,
        null,
        null
    )

    override fun getAllFiles(): List<FileEntry> {
        val files = mutableListOf<List<FileEntry>>()
        imprint?.let {
            files.add(imprint.getAllFiles())
        }
        sectionList.forEach { section ->
            files.add(section.getAllFiles())
            getArticleList().forEach { article ->
                files.add(article.getAllFiles())
            }
        }
        pageList.forEach { page ->
            files.add(page.getAllFiles())
        }
        return files.flatten().distinct()
    }

    override fun getAllFileNames(): List<String> {
        return getAllFiles().map { it.name }
    }

    private fun getArticleList(): List<Article> {
        val articleList = mutableListOf<Article>()
        sectionList.forEach {
            articleList.addAll(it.articleList)
        }
        return articleList
    }
}

@JsonClass(generateAdapter = false)
enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}
