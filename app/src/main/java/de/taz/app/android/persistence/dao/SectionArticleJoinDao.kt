package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.join.SectionArticleJoin


@Dao
interface SectionArticleJoinDao : BaseDao<SectionArticleJoin> {

    @Query(
        """ SELECT Article.* FROM Article INNER JOIN SectionArticleJoin
            ON Article.articleFileName == SectionArticleJoin.articleFileName
            WHERE SectionArticleJoin.sectionFileName == :sectionFileName ORDER BY SectionArticleJoin.`index` ASC
        """
    )
    suspend fun getArticlesForSection(sectionFileName: String): List<ArticleStub>?


    @Query(
        """ SELECT Section.* FROM Section INNER JOIN SectionArticleJoin
            ON Section.sectionFileName == SectionArticleJoin.sectionFileName
            WHERE SectionArticleJoin.articleFileName == :articleFileName LIMIT 1
            
        """
    )
    suspend fun getSectionStubForArticleFileName(articleFileName: String): SectionStub?

    suspend fun getSectionStubForArticle(article: Article): SectionStub?
            = getSectionStubForArticleFileName(article.articleHtml.name)

    @Query(
        """ SELECT Section.sectionFileName FROM Section INNER JOIN SectionArticleJoin
            ON Section.sectionFileName == SectionArticleJoin.sectionFileName
            WHERE SectionArticleJoin.articleFileName == :articleFileName LIMIT 1
            
        """
    )
    suspend fun getSectionFileNameForArticleFileName(articleFileName: String): String?

    suspend fun getSectionFileNameForArticle(article: Article): String?
            = getSectionFileNameForArticleFileName(article.articleHtml.name)


    @Query(""" SELECT Article.* FROM Article
         INNER JOIN SectionArticleJoin as SAJ1
         INNER JOIN SectionArticleJoin as SAJ2
          WHERE SAJ1.articleFileName == :articleFileName
          AND SAJ2.`index` == SAJ1.`index` + 1
          AND SAJ1.sectionFileName == SAJ2.sectionFileName
          AND Article.articleFileName == SAJ2.articleFileName
    """)
    suspend fun getNextArticleStubInSection(articleFileName: String): ArticleStub?

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
    suspend fun getNextArticleStubInNextSection(articleFileName: String): ArticleStub?




    @Query(""" SELECT Article.* FROM Article
         INNER JOIN SectionArticleJoin as SAJ1
         INNER JOIN SectionArticleJoin as SAJ2
          WHERE SAJ1.articleFileName == :articleFileName
          AND SAJ2.`index` == SAJ1.`index` - 1
          AND SAJ1.sectionFileName == SAJ2.sectionFileName
          AND Article.articleFileName == SAJ2.articleFileName
    """)
    suspend fun getPreviousArticleStubInSection(articleFileName: String): ArticleStub?

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
    suspend fun getPreviousArticleStubInPreviousSection(articleFileName: String): ArticleStub?


    @Query(
        """ SELECT `index` FROM SectionArticleJoin
            WHERE SectionArticleJoin.articleFileName == :articleFileName
        """
    )
    suspend fun getIndexOfArticleInSection(articleFileName: String): Int?

    @Query(
        """
        SELECT SectionArticleJoin.* FROM SectionArticleJoin
        INNER JOIN IssueSectionJoin
        WHERE IssueSectionJoin.issueFeedName == :issueFeedName
            AND IssueSectionJoin.issueDate == :issueDate
            AND IssueSectionJoin.issueStatus == :issueStatus
            AND SectionArticleJoin.sectionFileName == IssueSectionJoin.sectionFileName
        """
    )
    suspend fun getSectionArticleJoinsForIssue(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): List<SectionArticleJoin>


    @Query("DELETE FROM SectionArticleJoin WHERE sectionFileName = :sectionFileName")
    suspend fun deleteRelationToSection(sectionFileName: String)
}
