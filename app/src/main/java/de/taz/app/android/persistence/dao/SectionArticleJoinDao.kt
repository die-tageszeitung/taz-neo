package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ArticleBase
import de.taz.app.android.persistence.join.SectionArticleJoin


@Dao
abstract class SectionArticleJoinDao : BaseDao<SectionArticleJoin>() {

    @Query(
        """ SELECT Article.* FROM Article INNER JOIN SectionArticleJoin
            ON Article.articleFileName == SectionArticleJoin.articleFileName
            WHERE  SectionArticleJoin.sectionFileName == :sectionFileName
        """
    )
    abstract fun getArticlesForSection(sectionFileName: String): List<ArticleBase>?


    @Query(
        """ SELECT articleFileName FROM SectionArticleJoin
            WHERE SectionArticleJoin.sectionFileName == :sectionFileName
            ORDER BY SectionArticleJoin.`index` ASC
        """
    )
    abstract fun getArticleFileNamesForSection(sectionFileName: String): List<String>?
}
