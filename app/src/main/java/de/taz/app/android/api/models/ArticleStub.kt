package de.taz.app.android.api.models

import android.content.Context
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import java.util.Date

@Entity(
    tableName = "Article",
    foreignKeys = [
        ForeignKey(
            entity = AudioStub::class,
            parentColumns = ["fileName"],
            childColumns = ["audioFileName"]
        ),
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["name"],
            childColumns = ["pdfFileName"]
        )
    ],
    indices = [
        Index("audioFileName"),
        Index("pdfFileName"),
    ]
)
data class ArticleStub(
    @PrimaryKey val articleFileName: String,
    override val issueFeedName: String,
    override val issueDate: String,
    override val title: String?,
    override val teaser: String?,
    override val onlineLink: String?,
    override val pageNameList: List<String>,
    override val bookmarkedTime: Date?,
    val audioFileName: String?,
    override val articleType: ArticleType,
    override val position: Int,
    override val percentage: Int,
    override val dateDownload: Date?,
    override val mediaSyncId: Int?,
    override val chars: Int?,
    override val words: Int?,
    override val readMinutes: Int?,
    val pdfFileName: String?,
) : ArticleOperations {

    val hasAudio: Boolean
        get() = audioFileName != null

    constructor(article: Article) : this(
        article.articleHtml.name,
        article.issueFeedName,
        article.issueDate,
        article.title,
        article.teaser,
        article.onlineLink,
        article.pageNameList,
        article.bookmarkedTime,
        article.audio?.file?.name,
        article.articleType,
        article.position,
        article.percentage,
        article.dateDownload,
        article.mediaSyncId,
        article.chars,
        article.words,
        article.readMinutes,
        article.pdf?.name,
    )

    @Ignore
    override val key: String = articleFileName

    override fun getDownloadTag(): String {
        return this.key
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
        return ArticleRepository.getInstance(applicationContext).getDownloadDate(this)
    }

    override suspend fun setDownloadDate(date: Date?, applicationContext: Context) {
        return ArticleRepository.getInstance(applicationContext).setDownloadDate(this, date)
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


    override suspend fun getAllFiles(applicationContext: Context): List<FileEntry> {
        return ArticleRepository.getInstance(applicationContext).getFileNamesForArticle(articleFileName).mapNotNull {
            FileEntryRepository.getInstance(applicationContext).get(it)
        }
    }

    override suspend fun getAuthorNames(applicationContext: Context): String {
        val authorNames = ArticleRepository.getInstance(applicationContext).getAuthorNamesForArticle(articleFileName)
        return authorNames.joinToString(", ")
    }

}

/**
 * Used to update bookmark states with partial updates.
 * See https://developer.android.com/reference/androidx/room/Update
 */
data class ArticleBookmarkTime(
    val articleFileName: String,
    val bookmarkedTime: Date?,
)