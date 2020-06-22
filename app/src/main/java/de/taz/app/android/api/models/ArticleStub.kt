package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "Article")
data class ArticleStub(
    @PrimaryKey val articleFileName: String,
    val issueFeedName: String,
    val issueDate: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val pageNameList: List<String> = emptyList(),
    val bookmarked: Boolean = false,
    override val articleType: ArticleType = ArticleType.STANDARD,
    val position: Int = 0,
    val percentage: Int = 0,
    override val downloadedField: Boolean?
) : ArticleOperations {

    constructor(article: Article) : this(
        article.articleHtml.name,
        article.issueFeedName,
        article.issueDate,
        article.title,
        article.teaser,
        article.onlineLink,
        article.pageNameList,
        article.bookmarked,
        article.articleType,
        article.position,
        article.percentage,
        article.downloadedField
    )

    @Ignore
    override val key: String = articleFileName

    override fun getAllFileNames(): List<String> {
        val articleRepository = ArticleRepository.getInstance()
        val imageList = articleRepository.getImagesForArticle(key)
        val authorList = articleRepository.getAuthorImageFileNamesForArticle(key)

        val list = mutableListOf(key)
        list.addAll(authorList)
        list.addAll(imageList.filter { it.resolution == ImageResolution.normal }.map { it.name })
        return list.distinct()
    }

    suspend fun getFirstImage(): Image? = withContext(Dispatchers.IO) {
        ArticleRepository.getInstance().getImagesForArticle(this@ArticleStub.key)
            .firstOrNull()
    }

    override fun setIsDownloaded(downloaded: Boolean) {
        ArticleRepository.getInstance().update(this.copy(downloadedField = downloaded))
    }

}
