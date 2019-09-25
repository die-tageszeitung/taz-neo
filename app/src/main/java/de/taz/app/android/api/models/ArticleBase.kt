package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.ArticleFunctions

@Entity(tableName = "Article")
data class ArticleBase(
    @PrimaryKey override val articleFileName: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val pageNameList: List<String> = emptyList()
): ArticleFunctions {
    constructor(article: Article) : this(
        article.articleHtml.name,
        article.title,
        article.teaser,
        article.onlineLink,
        article.pageNameList
    )
}
