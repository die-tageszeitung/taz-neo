package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.PageStub
import java.util.*

@Dao
interface PageDao : BaseDao<PageStub> {
    @Query("SELECT Page.* FROM Page WHERE Page.pdfFileName == :fileName LIMIT 1")
    suspend fun get(fileName: String): PageStub?

    @Query("SELECT dateDownload FROM Page WHERE pdfFileName == :fileName")
    suspend fun getDownloadDate(fileName: String): Date?

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

    @Query(
        """
        SELECT Page.* FROM Page 
            INNER JOIN IssuePageJoin ON Page.pdfFileName = IssuePageJoin.pageKey
        WHERE IssuePageJoin.issueFeedName == :issueFeed 
            AND IssuePageJoin.issueDate == :issueDate 
            AND IssuePageJoin.issueStatus == :issueStatus
            ORDER BY IssuePageJoin.`index`
    """
    )
    suspend fun getPageStubListForIssue(
        issueFeed: String,
        issueDate: String,
        issueStatus: IssueStatus,
    ): List<PageStub>
}
