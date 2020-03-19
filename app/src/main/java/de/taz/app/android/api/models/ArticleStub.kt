package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "Article")
data class ArticleStub(
    @PrimaryKey override val articleFileName: String,
    val issueFeedName: String,
    val issueDate: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val pageNameList: List<String> = emptyList(),
    val bookmarked: Boolean = false,
    override val articleType: ArticleType = ArticleType.STANDARD,
    val position: Int = 0,
    val percentage: Int = 0
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
        article.percentage
    )

    override fun getAllFileNames(): List<String> {
        val articleRepository = ArticleRepository.getInstance()
        val imageList = articleRepository.getImagesForArticle(articleFileName)
        val authorList = articleRepository.getAuthorImageFileNamesForArticle(articleFileName)

        val list = mutableListOf(articleFileName)
        list.addAll(authorList)
        list.addAll(imageList.map { it.name }.filter { it.contains(".norm.") })
        return list.distinct()
    }

    suspend fun getFirstImage(): FileEntry? = withContext(Dispatchers.IO) {
        ArticleRepository.getInstance().getImagesForArticle(this@ArticleStub.articleFileName)
            .firstOrNull()
    }

}
