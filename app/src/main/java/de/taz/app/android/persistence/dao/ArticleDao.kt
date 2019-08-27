package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ArticleBase

@Dao
abstract class ArticleDao : BaseDao<ArticleBase>() {
    @Query("SELECT * FROM Article WHERE Article.articleFileName == :articleFileName LIMIT 1")
    abstract fun get(articleFileName: String): ArticleBase
}
