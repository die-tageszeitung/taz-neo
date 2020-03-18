package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ArticleDto
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.FileHelper
import java.io.File

data class Article(
    val articleHtml: FileEntry,
    val issueFeedName: String,
    val issueDate: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val audioFile: FileEntry?,
    val pageNameList: List<String> = emptyList(),
    val imageList: List<FileEntry> = emptyList(),
    val authorList: List<Author> = emptyList(),
    val articleType: ArticleType = ArticleType.STANDARD,
    val bookmarked: Boolean = false,
    val position: Int = 0,
    val percentage: Int = 0
) : ArticleOperations, CacheableDownload, WebViewDisplayable {

    constructor(
        issueFeedName: String,
        issueDate: String,
        articleDto: ArticleDto,
        articleType: ArticleType = ArticleType.STANDARD
    ) : this(
        FileEntry(articleDto.articleHtml, "$issueFeedName/$issueDate"),
        issueFeedName,
        issueDate,
        articleDto.title,
        articleDto.teaser,
        articleDto.onlineLink,
        articleDto.audioFile?.let { FileEntry(it, "$issueFeedName/$issueDate") },
        articleDto.pageNameList ?: emptyList(),
        articleDto.imageList?.map { FileEntry(it, "$issueFeedName/$issueDate") } ?: emptyList(),
        articleDto.authorList?.map { Author(it) } ?: emptyList(),
        articleType
    )

    override val articleFileName
        get() = articleHtml.name

    override suspend fun getAllFiles(): List<FileEntry> {
        val list = mutableListOf(articleHtml)
        list.addAll(authorList.mapNotNull { it.imageAuthor })
        list.addAll(imageList.filter { it.name.contains(".norm.") })
        return list
    }

    override fun getAllFileNames(): List<String> {
        val list = mutableListOf(articleHtml)
        list.addAll(authorList.mapNotNull { it.imageAuthor })
        list.addAll(imageList.filter { it.name.contains(".norm.") })
        return list.map { it.name }.distinct()
    }

    override fun getFile(): File? {
        return FileHelper.getInstance().getFile(articleHtml)
    }

    override fun previous(): Article? {
        return previousArticle()
    }

    override fun next(): Article? {
        return nextArticle()
    }

    fun isImprint(): Boolean {
        return articleType == ArticleType.IMPRINT
    }

    fun getIssueStub(): IssueStub? {
        return if (isImprint()) {
            IssueRepository.getInstance().getIssueStubByImprintFileName(articleFileName)
        } else {
            getSection()?.issueStub
        }
    }

    fun getIssue(): Issue? {
        return getIssueStub()?.let { IssueRepository.getInstance().getIssue(it) }
    }

    override fun getIssueOperations() = getIssueStub()

}
