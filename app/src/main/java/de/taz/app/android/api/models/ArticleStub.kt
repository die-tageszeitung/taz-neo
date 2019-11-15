package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.ArticleOperations

@Entity(tableName = "Article")
data class ArticleStub(
    @PrimaryKey override val articleFileName: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val pageNameList: List<String> = emptyList(),
    val bookmarked: Boolean = false,
    val isImprint: Boolean = false,
    val position: Int = 0,
    val percentage: Int = 0
): ArticleOperations {
    constructor(article: Article) : this(
        article.articleHtml.name,
        article.title,
        article.teaser,
        article.onlineLink,
        article.pageNameList,
        article.bookmarked,
        article.isImprint,
        article.position,
        article.percentage
    )
}
