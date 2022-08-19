package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.IssueStatus
import java.util.*

@Dao
interface ArticleDao : BaseDao<ArticleStub> {
    @Query("SELECT * FROM Article WHERE Article.articleFileName == :articleFileName LIMIT 1")
    suspend fun get(articleFileName: String): ArticleStub?

    @Query("SELECT * FROM Article WHERE Article.articleFileName == :articleFileName LIMIT 1")
    fun getLiveData(articleFileName: String): LiveData<ArticleStub>

    @Query("SELECT * FROM Article WHERE Article.articleFileName in (:articleFileNames)")
    suspend fun get(articleFileNames: List<String>): List<ArticleStub>

    @Query("SELECT * FROM Article WHERE Article.bookmarkedTime IS NOT NULL ORDER BY Article.bookmarkedTime DESC")
    fun getBookmarkedArticlesLiveData(): LiveData<List<ArticleStub>>

    @Query("SELECT * FROM Article WHERE Article.bookmarkedTime IS NOT NULL ORDER BY Article.bookmarkedTime DESC")
    suspend fun getBookmarkedArticles(): List<ArticleStub>

    @Query(
        """SELECT Article.* FROM Article INNER JOIN SectionArticleJoin INNER JOIN SectionArticleJoin as SAJ
            ON Article.articleFileName == SAJ.articleFileName
        WHERE SectionArticleJoin.articleFileName == :articleFileName
            AND SAJ.sectionFileName == SectionArticleJoin.sectionFileName
            ORDER BY SAJ.`index` ASC
    """
    )
    suspend fun getSectionArticleListByArticle(articleFileName: String): List<ArticleStub>

    @Query(
        """SELECT Article.* FROM Article
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
    """
    )
    suspend fun getIssueArticleListByArticle(articleFileName: String): List<ArticleStub>

    @Query("SELECT EXISTS (SELECT * FROM Article WHERE articleFileName == :articleFileName AND dateDownload IS NOT NULL)")
    fun isDownloadedLiveData(articleFileName: String): LiveData<Boolean>

    @Query("SELECT dateDownload FROM Article WHERE articleFileName == :articleFileName")
    suspend fun getDownloadStatus(articleFileName: String): Date?

    @Query(
        """SELECT Article.* FROM Article
        INNER JOIN SectionArticleJoin
        INNER JOIN IssueSectionJoin
        WHERE IssueSectionJoin.issueFeedName == :issueFeedName
            AND IssueSectionJoin.issueDate == :issueDate
            AND IssueSectionJoin.issueStatus == :issueStatus
            AND SectionArticleJoin.sectionFileName == IssueSectionJoin.sectionFileName
            AND Article.articleFileName == SectionArticleJoin.articleFileName
        ORDER BY IssueSectionJoin.`index` ASC , SectionArticleJoin.`index` ASC
    """
    )
    suspend fun getArticleStubListForIssue(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): List<ArticleStub>

    @Query(
        """SELECT Article.* FROM Article
        INNER JOIN SectionArticleJoin
        INNER JOIN IssueSectionJoin
        WHERE IssueSectionJoin.issueFeedName == :issueFeedName
            AND IssueSectionJoin.issueDate == :issueDate
            AND IssueSectionJoin.issueStatus == :issueStatus
            AND SectionArticleJoin.sectionFileName == IssueSectionJoin.sectionFileName
            AND Article.articleFileName == SectionArticleJoin.articleFileName
            AND Article.bookmarkedTime IS NOT NULL 
        ORDER BY IssueSectionJoin.`index` ASC , SectionArticleJoin.`index` ASC
    """
    )
    suspend fun getBookmarkedArticleStubsForIssue(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): List<ArticleStub>

    @Query(
        """
            SELECT COUNT(DISTINCT ArticleAuthor.articleFileName) FROM ArticleAuthor 
            INNER JOIN Article ON ArticleAuthor.articleFileName = Article.articleFileName
            WHERE 
                authorFileName = :authorFileName AND
                Article.dateDownload IS NOT NULL
    """
    )
    suspend fun getDownloadedArticleAuthorReferenceCount(authorFileName: String): Int

    @Query(
        """SELECT COUNT(DISTINCT ArticleImageJoin.articleFileName) FROM ArticleImageJoin 
            
            INNER JOIN Article ON ArticleImageJoin.articleFileName = Article.articleFileName
            WHERE
                imageFileName = :articleImageFileName AND
                Article.dateDownload IS NOT NULL
    """
    )
    suspend fun getDownloadedArticleImageReferenceCount(articleImageFileName: String): Int
}
