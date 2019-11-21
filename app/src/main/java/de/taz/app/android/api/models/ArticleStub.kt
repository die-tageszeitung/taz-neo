package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.ArticleOperations

@Entity(tableName = "Article")
data class ArticleStub(
    @PrimaryKey override val articleFileName: String,
    val date: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val pageNameList: List<String> = emptyList(),
    val bookmarked: Boolean = false,
    val articleType: ArticleType = ArticleType.STANDARD,
    val position: Int = 0,
    val percentage: Int = 0
): ArticleOperations {
    constructor(article: Article) : this(
        article.articleHtml.name,
        article.date,
        article.title,
        article.teaser,
        article.onlineLink,
        article.pageNameList,
        article.bookmarked,
        article.articleType,
        article.position,
        article.percentage
    )
}
