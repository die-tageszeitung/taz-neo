package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin


@Dao
interface ArticleAuthorImageJoinDao : BaseDao<ArticleAuthorImageJoin> {

    @Query(
        """SELECT ArticleAuthor.* FROM ArticleAuthor INNER JOIN Article
        ON ArticleAuthor.articleFileName == Article.articleFileName 
        WHERE ArticleAuthor.articleFileName == :articleFileName
        ORDER BY ArticleAuthor.`index` ASC
    """
    )
    suspend fun getAuthorImageJoinForArticle(articleFileName: String): List<ArticleAuthorImageJoin>

    @Query("DELETE FROM ArticleAuthor WHERE articleFileName = :articleFileName")
    suspend fun deleteRelationToArticle(articleFileName: String)

    @Query(
        """SELECT DISTINCT ArticleAuthor.* FROM ArticleAuthor INNER JOIN Article
        ON ArticleAuthor.articleFileName == Article.articleFileName 
        WHERE ArticleAuthor.authorFileName == :authorFileName
        ORDER BY ArticleAuthor.`index` ASC
    """
    )
    suspend fun getArticlesForAuthor(authorFileName: String): List<ArticleAuthorImageJoin>
}
