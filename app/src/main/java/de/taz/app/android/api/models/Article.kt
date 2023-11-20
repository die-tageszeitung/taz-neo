package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.persistence.repository.ArticleRepository
import java.util.Date

data class Article(
    val articleHtml: FileEntry,
    val issueFeedName: String,
    val issueDate: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val audio: Audio?,
    val pageNameList: List<String>,
    val imageList: List<Image>,
    val authorList: List<Author>,
    val mediaSyncId: Int?,
    val chars: Int?,
    val words: Int?,
    val readMinutes: Int?,
    override val articleType: ArticleType,
    val bookmarkedTime: Date?,
    val position: Int,
    val percentage: Int,
    override val dateDownload: Date?
) : ArticleOperations, WebViewDisplayable {

    override val path: String
        get() = articleHtml.path

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

    override suspend fun previous(applicationContext: Context): Article? {
        val articleRepository = ArticleRepository.getInstance(applicationContext)
        return articleRepository.previousArticleStub(key)?.let {
            articleRepository.articleStubToArticle(it)
        }
    }

    override suspend fun next(applicationContext: Context): Article? {
        val articleRepository = ArticleRepository.getInstance(applicationContext)
        return articleRepository.nextArticleStub(key)?.let {
            articleRepository.articleStubToArticle(it)
        }
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

    /**
     * If articles file name contains "public" we assume the corresponding issue has [IssueStatus.public]
     * otherwise we assume an issue with status [IssueStatus.regular]
     */
    fun guessIssueStatusByArticleFileName(): IssueStatus {
        return if (key.endsWith("public.html")) {
            IssueStatus.public
        }
        else {
            IssueStatus.regular
        }
    }
}

enum class ArticleType {
    STANDARD, IMPRINT;
}
