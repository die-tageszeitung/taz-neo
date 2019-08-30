package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleBase
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin


@Dao
abstract class ArticleAuthorImageJoinDao : BaseDao<ArticleAuthorImageJoin>() {

    @Query(
        """SELECT ArticleAuthor.* FROM ArticleAuthor INNER JOIN Article
        ON ArticleAuthor.articleFileName == Article.articleFileName 
        WHERE ArticleAuthor.articleFileName == :articleFileName
    """
    )
    abstract fun getAuthorImageJoinForArticle(articleFileName: String): List<ArticleAuthorImageJoin>?

    fun getAuthorImageJoinForArticle(article: Article) = getAuthorImageJoinForArticle(article.articleHtml.name)

    fun getAuthorImageJoinForArticle(articleBase: ArticleBase) =
        getAuthorImageJoinForArticle(articleBase.articleFileName)


}
