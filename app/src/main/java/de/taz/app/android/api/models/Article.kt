package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.persistence.repository.ArticleRepository
import java.util.Date

data class Article(
    val articleHtml: FileEntry,
    override val issueFeedName: String,
    override val issueDate: String,
    override val title: String?,
    override val teaser: String?,
    override val onlineLink: String?,
    val audio: Audio?,
    override val pageNameList: List<String>,
    val imageList: List<Image>,
    val authorList: List<Author>,
    override val mediaSyncId: Int?,
    override val chars: Int?,
    override val words: Int?,
    override val readMinutes: Int?,
    override val articleType: ArticleType,
    override val bookmarkedTime: Date?,
    override val position: Int,
    override val percentage: Int,
    override val dateDownload: Date?,
    val pdf: FileEntry?,
) : ArticleOperations {

    val bookmarked = bookmarkedTime != null

    override val key: String
        get() = articleHtml.name

    override suspend fun getAllFiles(applicationContext: Context): List<FileEntry> {
        val list = mutableListOf(articleHtml)
        list.addAll(authorList.mapNotNull { it.imageAuthor })
        list.addAll(imageList.filter { it.resolution == ImageResolution.normal }
            .map { FileEntry(it) })
        return list.distinct()
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
        return ArticleRepository.getInstance(applicationContext)
            .getDownloadDate(ArticleStub(this@Article))
    }

    override suspend fun setDownloadDate(date: Date?, applicationContext: Context) {
        return ArticleRepository.getInstance(applicationContext)
            .setDownloadDate(ArticleStub(this@Article), date)
    }

    /**
     * If articles file name contains "public" we assume the corresponding issue has [IssueStatus.public]
     * otherwise we assume an issue with status [IssueStatus.regular]
     */
    fun guessIssueStatusByArticleFileName(): IssueStatus {
        return if (key.endsWith("public.html")) {
            IssueStatus.public
        } else {
            IssueStatus.regular
        }
    }

    override suspend fun getAuthorNames(applicationContext: Context): String {
        return if (authorList.isNotEmpty()) {
            authorList.map { it.name }.distinct().joinToString(", ")
        } else {
            ""
        }
    }
}

enum class ArticleType {
    STANDARD, IMPRINT;
}
