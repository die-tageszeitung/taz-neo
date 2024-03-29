package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.join.IssueImprintJoin


@Dao
interface IssueImprintJoinDao : BaseDao<IssueImprintJoin> {

    @Query(
        """SELECT Article.articleFileName FROM Article INNER JOIN IssueImprintJoin
        ON Article.articleFileName == IssueImprintJoin.articleFileName
        WHERE  IssueImprintJoin.issueDate == :date AND IssueImprintJoin.issueFeedName == :feedName
            AND IssueImprintJoin.issueStatus == :status
        """
    )
    suspend fun getArticleImprintNameForIssue(
        feedName: String,
        date: String,
        status: IssueStatus
    ): String?

    @Query(
        """SELECT articleFileName
            FROM IssueImprintJoin
            WHERE issueDate == :date
            AND issueFeedName == :feedName
            AND issueStatus == :status
        """
    )
    suspend fun getLeftOverImprintNameForIssue(
        feedName: String,
        date: String,
        status: IssueStatus
    ): String?


    @Query(
        """SELECT Issue.* FROM Issue INNER JOIN IssueImprintJoin
        ON Issue.date == IssueImprintJoin.issueDate
            AND Issue.status == IssueImprintJoin.issueStatus 
            AND Issue.feedName == IssueImprintJoin.issueFeedName
        WHERE IssueImprintJoin.articleFileName == :imprintFileName
        """
    )
    suspend fun getIssueForImprintFileName(imprintFileName: String): IssueStub?

    @Query("DELETE FROM IssueImprintJoin WHERE issueFeedName = :feedName AND issueDate = :date AND issueStatus = :status")
    suspend fun deleteRelationToIssue(feedName: String, date: String, status: IssueStatus)
}
