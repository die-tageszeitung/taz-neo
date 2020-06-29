package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ArticleStub

@Dao
abstract class ArticleDao : BaseDao<ArticleStub>() {
    @Query("SELECT * FROM Article WHERE Article.articleFileName == :articleFileName LIMIT 1")
    abstract fun get(articleFileName: String): ArticleStub?

    @Query("SELECT * FROM Article WHERE Article.articleFileName == :articleFileName LIMIT 1")
    abstract fun getLiveData(articleFileName: String): LiveData<ArticleStub?>

    @Query("SELECT * FROM Article WHERE Article.articleFileName in (:articleFileNames)")
    abstract fun get(articleFileNames: List<String>): List<ArticleStub>

    @Query("SELECT * FROM Article WHERE Article.bookmarked != 0")
    abstract fun getBookmarkedArticlesLiveData(): LiveData<List<ArticleStub>>

    @Query("SELECT * FROM Article WHERE Article.bookmarked != 0")
    abstract fun getBookmarkedArticlesList(): List<ArticleStub>

    @Query("""SELECT Article.* FROM Article INNER JOIN SectionArticleJoin INNER JOIN SectionArticleJoin as SAJ
            ON Article.articleFileName == SAJ.articleFileName
        WHERE SectionArticleJoin.articleFileName == :articleFileName
            AND SAJ.sectionFileName == SectionArticleJoin.sectionFileName
            ORDER BY SAJ.`index` ASC
    """)
    abstract fun getSectionArticleListByArticle(articleFileName: String): List<ArticleStub>

    @Query("""SELECT Article.* FROM Article
        INNER JOIN SectionArticleJoin as Name
        INNER JOIN SectionArticleJoin as SAJ
        INNER JOIN IssueSectionJoin as ISJConstraint
        INNER JOIN IssueSectionJoin
            ON Article.articleFileName == SAJ.articleFileName
        WHERE Name.articleFileName == :articleFileName
            AND ISJConstraint.sectionFileName == Name.sectionFileName
            AND SAJ.sectionFileName == IssueSectionJoin.sectionFileName
            AND ISJConstraint.issueStatus == IssueSectionJoin.issueStatus
            AND ISJConstraint.issueFeedName == IssueSectionJoin.issueFeedName
            AND ISJConstraint.issueDate == IssueSectionJoin.issueDate
            ORDER BY IssueSectionJoin.`index` ASC , SAJ.`index` ASC
    """)
    abstract fun getIssueArticleListByArticle(articleFileName: String): List<ArticleStub>

    @Query("SELECT EXISTS (SELECT * FROM Article WHERE articleFileName == :articleFileName AND downloadedStatus == 'done')")
    abstract fun isDownloadedLiveData(articleFileName: String): LiveData<Boolean>

}
