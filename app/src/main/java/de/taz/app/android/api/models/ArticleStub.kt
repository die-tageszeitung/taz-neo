package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.ArticleOperations
import java.util.*

@Entity(tableName = "Article")
data class ArticleStub(
    @PrimaryKey val articleFileName: String,
    val issueFeedName: String,
    val issueDate: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val pageNameList: List<String>,
    val bookmarkedTime: Date?,
    override val articleType: ArticleType,
    val position: Int,
    val percentage: Int,
    override val dateDownload: Date?
) : ArticleOperations {

    constructor(article: Article) : this(
        article.articleHtml.name,
        article.issueFeedName,
        article.issueDate,
        article.title,
        article.teaser,
        article.onlineLink,
        article.pageNameList,
        article.bookmarkedTime,
        article.articleType,
        article.position,
        article.percentage,
        article.dateDownload
    )

    @Ignore
    override val key: String = articleFileName

}
