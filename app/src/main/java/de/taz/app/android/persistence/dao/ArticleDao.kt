package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ArticleBase

@Dao
abstract class ArticleDao : BaseDao<ArticleBase>() {
    @Query("SELECT * FROM Article WHERE Article.articleFileName == :articleFileName LIMIT 1")
    abstract fun get(articleFileName: String): ArticleBase

    @Query("SELECT * FROM Article WHERE Article.articleFileName == :articleFileName LIMIT 1")
    abstract fun getLiveData(articleFileName: String): LiveData<ArticleBase?>

    @Query("SELECT * FROM Article WHERE Article.articleFileName in(:articleFileNames)")
    abstract fun get(articleFileNames: List<String>): List<ArticleBase>

    @Query("SELECT * FROM Article WHERE Article.bookmarked != 0")
    abstract fun getBookmarkedArticlesLiveData(): LiveData<List<ArticleBase>>

}
