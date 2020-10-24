package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ArticleDto
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.singletons.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    val bookmarked: Boolean,
    val position: Int,
    val percentage: Int,
    override val dateDownload: Date?
) : ArticleOperations, WebViewDisplayable {

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
        articleDto.imageList?.map { Image(it, "$issueFeedName/$issueDate") } ?: emptyList(),
        articleDto.authorList?.map { Author(it) } ?: emptyList(),
        articleType,
        false,
        0,
        0,
        null
    )

    override val key: String
        get() = articleHtml.name

    override fun getAllFiles(): List<FileEntry> {
        val list = mutableListOf(articleHtml)
        list.addAll(authorList.mapNotNull { it.imageAuthor })
        list.addAll(imageList.filter { it.resolution == ImageResolution.normal }
            .map { FileEntry(it) })
        return list.distinct()
    }

    override fun getAllFileNames(): List<String> {
        return getAllFiles().map { it.name }.distinct()
    }

    override suspend fun deleteFiles() {
        getAllFileNames().forEach { FileHelper.getInstance().deleteFile(it) }
    }

    override fun getDownloadTag(): String {
        return articleHtml.name
    }


    fun nextArticle(): Article? {
        val articleRepository = ArticleRepository.getInstance()
        return articleRepository.nextArticleStub(key)?.let {
            articleRepository.articleStubToArticle(it)
        }
    }


    fun previousArticle(): Article? {
        val articleRepository = ArticleRepository.getInstance()
        return articleRepository.previousArticleStub(key)?.let {
            articleRepository.articleStubToArticle(it)
        }
    }

    override fun getFile(): File? {
        return FileHelper.getInstance().getFile(this.key)
    }

    override fun getFilePath(): String? {
        return FileHelper.getInstance().getAbsoluteFilePath(this.key)
    }

    override fun previous(): Article? {
        return previousArticle()
    }

    override fun next(): Article? {
        return nextArticle()
    }

    override fun getIssueStub(): IssueStub? {
        return super.getIssueStub()
    }

    override suspend fun getDownloadDate(): Date? = withContext(Dispatchers.IO) {
        ArticleRepository.getInstance().getDownloadDate(ArticleStub(this@Article))
    }

    override suspend fun setDownloadDate(date: Date?) {
        ArticleRepository.getInstance().setDownloadDate(ArticleStub(this@Article), date)
    }

}

enum class ArticleType {
    STANDARD, IMPRINT;
}
