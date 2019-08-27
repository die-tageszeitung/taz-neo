package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.ArticleDto

@Entity(tableName = "Article")
class ArticleBase(
    @PrimaryKey val articleFileName: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val pageNameList: List<String>?
    // TODO how to save ?! val authorList: List<Author>?
) {
    constructor(article: Article) : this(
        article.articleHtml.name,
        article.title,
        article.teaser,
        article.onlineLink,
        article.pageNameList
    )
}
