package de.taz.app.android.persistence.dao

import androidx.room.*
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.join.ArticleImageJoin


@Dao
abstract class ArticleImageJoinDao : BaseDao<ArticleImageJoin>() {

    @Query(
        """SELECT FileEntry.* FROM FileEntry INNER JOIN ArticleImageJoin
        ON FileEntry.name = ArticleImageJoin.imageFileName
        WHERE ArticleImageJoin.articleFileName == :articleFileName
    """
    )
    abstract fun getImagesForArticle(articleFileName: String): List<FileEntry>?

    fun getImageFilesForArticle(article: Article): List<FileEntry>? =
        getImagesForArticle(article.articleHtml.name)
}
