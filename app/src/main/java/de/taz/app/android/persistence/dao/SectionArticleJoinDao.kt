package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.join.SectionArticleJoin


@Dao
abstract class SectionArticleJoinDao : BaseDao<SectionArticleJoin>() {

    @Query(
        """ SELECT Article.* FROM Article INNER JOIN SectionArticleJoin
            ON Article.articleFileName == SectionArticleJoin.articleFileName
            WHERE SectionArticleJoin.sectionFileName == :sectionFileName
        """
    )
    abstract fun getArticlesForSection(sectionFileName: String): List<ArticleStub>?


    @Query(
        """ SELECT articleFileName FROM SectionArticleJoin
            WHERE SectionArticleJoin.sectionFileName == :sectionFileName
            ORDER BY SectionArticleJoin.`index` ASC
        """
    )
    abstract fun getArticleFileNamesForSection(sectionFileName: String): List<String>?


    @Query(
        """ SELECT Section.* FROM Section INNER JOIN SectionArticleJoin
            ON Section.sectionFileName == SectionArticleJoin.sectionFileName
            WHERE SectionArticleJoin.articleFileName == :articleFileName LIMIT 1
            
        """
    )
    abstract fun getSectionStubForArticleFileName(articleFileName: String): SectionStub?

    fun getSectionStubForArticleStub(articleStub: ArticleStub): SectionStub?
            = getSectionStubForArticleFileName(articleStub.articleFileName)

    fun getSectionStubForArticle(article: Article): SectionStub?
            = getSectionStubForArticleFileName(article.articleHtml.name)

    @Query(
        """ SELECT Section.sectionFileName FROM Section INNER JOIN SectionArticleJoin
            ON Section.sectionFileName == SectionArticleJoin.sectionFileName
            WHERE SectionArticleJoin.articleFileName == :articleFileName LIMIT 1
            
        """
    )
    abstract fun getSectionFileNameForArticleFileName(articleFileName: String): String?

    fun getSectionFileNameForArticleStub(articleStub: ArticleStub): String?
            = getSectionFileNameForArticleFileName(articleStub.articleFileName)

    fun getSectionFileNameForArticle(article: Article): String?
            = getSectionFileNameForArticleFileName(article.articleHtml.name)


    @Query(""" SELECT Article.* FROM Article
         INNER JOIN SectionArticleJoin as SAJ1
         INNER JOIN SectionArticleJoin as SAJ2
          WHERE SAJ1.articleFileName == :articleFileName
          AND SAJ2.`index` == SAJ1.`index` + 1
          AND SAJ1.sectionFileName == SAJ2.sectionFileName
          AND Article.articleFileName == SAJ2.articleFileName
    """)
    abstract fun getNextArticleStubInSection(articleFileName: String): ArticleStub?

    @Query(
        """ SELECT Article.* FROM Article
         INNER JOIN SectionArticleJoin as SAJ1
         INNER JOIN SectionArticleJoin as SAJ2
         INNER JOIN IssueSectionJoin as ISJ1
         INNER JOIN IssueSectionJoin as ISJ2
          WHERE SAJ1.articleFileName == :articleFileName
          AND ISJ1.sectionFileName == SAJ1.sectionFileName
          AND ISJ2.`index` == ISJ1.`index` + 1
          AND ISJ1.issueFeedName == ISJ2.issueFeedName
          AND ISJ1.issueDate == ISJ2.issueDate
          AND SAJ2.sectionFileName == ISJ2.sectionFileName
          AND SAJ2.`index` == 0
          AND Article.articleFileName == SAJ2.articleFileName
    """
    )
    abstract fun getNextArticleStubInNextSection(articleFileName: String): ArticleStub?




    @Query(""" SELECT Article.* FROM Article
         INNER JOIN SectionArticleJoin as SAJ1
         INNER JOIN SectionArticleJoin as SAJ2
          WHERE SAJ1.articleFileName == :articleFileName
          AND SAJ2.`index` == SAJ1.`index` - 1
          AND SAJ1.sectionFileName == SAJ2.sectionFileName
          AND Article.articleFileName == SAJ2.articleFileName
    """)
    abstract fun getPreviousArticleStubInSection(articleFileName: String): ArticleStub?

    @Query(
        """ SELECT Article.* FROM Article
         INNER JOIN SectionArticleJoin as SAJ1
         INNER JOIN SectionArticleJoin as SAJ2
         INNER JOIN IssueSectionJoin as ISJ1
         INNER JOIN IssueSectionJoin as ISJ2
          WHERE SAJ1.articleFileName == :articleFileName
          AND ISJ1.sectionFileName == SAJ1.sectionFileName
          AND ISJ2.`index` == ISJ1.`index` - 1
          AND ISJ1.issueFeedName == ISJ2.issueFeedName
          AND ISJ1.issueDate == ISJ2.issueDate
          AND SAJ2.sectionFileName == ISJ2.sectionFileName
          AND Article.articleFileName == SAJ2.articleFileName
          ORDER BY SAJ2.`index` DESC
          LIMIT 1
      """
    )
    abstract fun getPreviousArticleStubInPreviousSection(articleFileName: String): ArticleStub?


    @Query(
        """ SELECT `index` FROM SectionArticleJoin
            WHERE SectionArticleJoin.articleFileName == :articleFileName
        """
    )
    abstract fun getIndexOfArticleInSection(articleFileName: String): Int?

}
