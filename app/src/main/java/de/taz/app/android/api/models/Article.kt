package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.dto.ArticleDto
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.StorageService
import java.util.*

data class Article(
    val articleHtml: FileEntry,
    val issueFeedName: String,
    val issueDate: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val audioFile: FileEntry?,
    val pageNameList: List<String>,
    val imageList: List<Image>,
    val authorList: List<Author>,
    override val articleType: ArticleType,
    val bookmarkedTime: Date?,
    val position: Int,
    val percentage: Int,
    override val dateDownload: Date?
) : ArticleOperations, WebViewDisplayable {

    override val path: String
        get() = articleHtml.path

    constructor(
        issueKey: IssueKey,
        articleDto: ArticleDto,
        articleType: ArticleType = ArticleType.STANDARD
    ) : this(
        FileEntry(articleDto.articleHtml, StorageService.determineFilePath(articleDto.articleHtml, issueKey)),
        issueKey.feedName,
        issueKey.date,
        articleDto.title,
        articleDto.teaser,
        articleDto.onlineLink,
        articleDto.audioFile?.let { FileEntry(articleDto.audioFile, StorageService.determineFilePath(it, issueKey)) },
        articleDto.pageNameList ?: emptyList(),
        articleDto.imageList?.map { Image(it, StorageService.determineFilePath(it, issueKey)) } ?: emptyList(),
        articleDto.authorList?.map { Author(it) } ?: emptyList(),
        articleType,
        bookmarkedTime = null,
        0,
        0,
        null
    )
    val bookmarked = bookmarkedTime != null

    override val key: String
        get() = articleHtml.name

    override suspend fun getAllFiles(): List<FileEntry> {
        val list = mutableListOf(articleHtml)
        list.addAll(authorList.mapNotNull { it.imageAuthor })
        list.addAll(imageList.filter { it.resolution == ImageResolution.normal }
            .map { FileEntry(it) })
        return list.distinct()
    }

    override suspend fun getAllFileNames(): List<String> {
        return getAllFiles().map { it.name }.distinct()
    }

    override fun getDownloadTag(): String {
        return articleHtml.name
    }


    suspend fun nextArticle(applicationContext: Context): Article? {
        val articleRepository = ArticleRepository.getInstance(applicationContext)
        return articleRepository.nextArticleStub(key)?.let {
            articleRepository.articleStubToArticle(it)
        }
    }


    suspend fun previousArticle(applicationContext: Context): Article? {
        val articleRepository = ArticleRepository.getInstance(applicationContext)
        return articleRepository.previousArticleStub(key)?.let {
            articleRepository.articleStubToArticle(it)
        }
    }

    override suspend fun previous(applicationContext: Context): Article? {
        return previousArticle(applicationContext)
    }

    override suspend fun next(applicationContext: Context): Article? {
        return nextArticle(applicationContext)
    }

    override suspend fun getIssueStub(applicationContext: Context): IssueStub? {
        return super.getIssueStub(applicationContext)
    }

    override suspend fun getDownloadDate(applicationContext: Context): Date? {
        return ArticleRepository.getInstance(applicationContext).getDownloadDate(ArticleStub(this@Article))
    }

    override suspend fun setDownloadDate(date: Date?, applicationContext: Context) {
        return ArticleRepository.getInstance(applicationContext).setDownloadDate(ArticleStub(this@Article), date)
    }

}

enum class ArticleType {
    STANDARD, IMPRINT;
}
