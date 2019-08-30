package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Article")
class ArticleBase(
    @PrimaryKey val articleFileName: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val pageNameList: List<String>
) {
    constructor(article: Article) : this(
        article.articleHtml.name,
        article.title,
        article.teaser,
        article.onlineLink,
        article.pageNameList ?: listOf()
    )
}
