package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.persistence.repository.ArticleRepository
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
    override val moTime: String,
    override val dateDownload: Date?,
    override val lastDisplayableName: String?
) : IssueOperations, DownloadableCollection {

    constructor(feedName: String, issueDto: IssueDto) : this(
        feedName,
        issueDto.date,
        Moment(feedName, issueDto.date, issueDto.status, issueDto.baseUrl, issueDto.moment),
        issueDto.key,
        issueDto.baseUrl,
        issueDto.status,
        issueDto.minResourceVersion,
        issueDto.imprint?.let { Article(feedName, issueDto.date, it, ArticleType.IMPRINT) },
        issueDto.isWeekend,
        issueDto.sectionList?.map { Section(feedName, issueDto.date, it) } ?: emptyList(),
        issueDto.pageList?.map { Page(feedName, issueDto.date, it) } ?: emptyList(),
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

    override suspend fun deleteFiles() {
        val filesToDelete: MutableList<FileEntryOperations> = getAllFiles().toMutableList()
        val filesToRetain = sectionList.fold(mutableListOf<String>()) { acc, section ->
            // bookmarked articles should remain
            acc.addAll(
                section.articleList
                    .filter { it.bookmarked }
                    .map { it.getAllFileNames() }
                    .flatten()
                    .distinct()
            )
            // author images are potentially used globally so we retain them for now as they don't eat up much space
            acc.addAll(
                section.articleList
                    .map { it.authorList }
                    .flatten()
                    .mapNotNull { it.imageAuthor }
                    .map { it.name }
            )
            acc
        }
        filesToDelete.removeAll { it.name in filesToRetain }

        // do not delete bookmarked files
        ArticleRepository.getInstance().apply {
            getBookmarkedArticleStubListForIssuesAtDate(feedName, date).forEach {
                filesToDelete.removeAll(articleStubToArticle(it).getAllFiles())
            }
        }
        filesToDelete.forEach { it.deleteFile() }

        this.setDownloadDate(null)
    }
}

@JsonClass(generateAdapter = false)
enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}
