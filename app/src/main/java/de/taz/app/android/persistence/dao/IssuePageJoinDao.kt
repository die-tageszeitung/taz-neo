package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.PageStub
import de.taz.app.android.persistence.join.IssuePageJoin


@Dao
interface IssuePageJoinDao : BaseDao<IssuePageJoin> {

    @Query(
        """SELECT Page.* FROM Page INNER JOIN IssuePageJoin 
        ON Page.pdfFileName = IssuePageJoin.pageKey 
        WHERE  IssuePageJoin.issueDate == :date
            AND IssuePageJoin.issueFeedName == :feedName
            AND IssuePageJoin.`index` == 0
            AND IssuePageJoin.issueStatus == :status
        ORDER BY IssuePageJoin.`index` ASC
        """
    )
    suspend fun getFrontPageForIssue(feedName: String, date: String, status: IssueStatus): PageStub?

    @Query(
        """SELECT Page.pdfFileName FROM Page INNER JOIN IssuePageJoin 
        ON Page.pdfFileName = IssuePageJoin.pageKey 
        WHERE  IssuePageJoin.issueDate == :date AND IssuePageJoin.issueFeedName == :feedName
            AND IssuePageJoin.issueStatus == :status
        ORDER BY IssuePageJoin.`index` ASC
        """
    )
    suspend fun getPageNamesForIssue(feedName: String, date: String, status: IssueStatus): List<String>

    @Query("DELETE FROM IssuePageJoin WHERE issueFeedName = :feedName AND issueDate = :date AND issueStatus = :status")
    suspend fun deleteRelationToIssue(feedName: String, date: String, status: IssueStatus)
}
