package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageStub
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao : BaseDao<PageStub> {
    @Transaction
    @Query("SELECT * FROM Page WHERE Page.pdfFileName == :fileName LIMIT 1")
    suspend fun get(fileName: String): Page?

    @Query("""
        DELETE FROM FileEntry
        WHERE 
            name in (:pageNames) AND
            (name NOT IN (SELECT pageKey FROM IssuePageJoin))
    """)
    suspend fun deletePageFileEntriesIfNoIssueRelated(pageNames: List<String>)

    @Query("""
        DELETE FROM Page
        WHERE 
            pdfFileName in (:pageNames) AND
            (pdfFileName NOT IN (SELECT pageKey FROM IssuePageJoin))
    """)
    suspend fun deleteIfNoIssueRelated(pageNames: List<String>)

    @Transaction
    @Query(
        """
        SELECT Page.* FROM Page 
            INNER JOIN IssuePageJoin ON Page.pdfFileName = IssuePageJoin.pageKey
        WHERE IssuePageJoin.issueFeedName == :issueFeed 
            AND IssuePageJoin.issueDate == :issueDate 
            AND IssuePageJoin.issueStatus == :issueStatus
            ORDER BY IssuePageJoin.`index` ASC
    """
    )
    suspend fun getPageListForIssue(
        issueFeed: String,
        issueDate: String,
        issueStatus: IssueStatus,
    ): List<Page>

    @Transaction
    @Query(
        """
        SELECT Page.* FROM Page
            INNER JOIN IssuePageJoin ON Page.pdfFileName = IssuePageJoin.pageKey
        WHERE IssuePageJoin.issueFeedName == :issueFeed
            AND IssuePageJoin.issueDate == :issueDate
            AND IssuePageJoin.issueStatus == :issueStatus
            ORDER BY IssuePageJoin.`index` ASC
    """
    )
    fun getPageFlowForIssue(
        issueFeed: String,
        issueDate: String,
        issueStatus: IssueStatus,
    ): Flow<List<Page>>

    @Transaction
    @Query(""" 
        SELECT Page.* FROM Page
         WHERE NOT EXISTS ( SELECT 1 FROM IssuePageJoin WHERE IssuePageJoin.pageKey = Page.pdfFileName )
    """)
    suspend fun getOrphanedPages(): List<Page>
}
