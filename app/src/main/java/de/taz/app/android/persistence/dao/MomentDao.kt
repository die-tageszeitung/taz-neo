package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.MomentStub
import java.util.Date

@Dao
interface MomentDao: BaseDao<MomentStub> {

    @Query(
        """
        SELECT dateDownload FROM Moment
        WHERE Moment.issueStatus == :issueStatus
            AND Moment.issueFeedName == :issueFeedName
            AND Moment.issueDate == :issueDate
        """
    )
    suspend fun getDownloadDate(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): Date?

    @Query(
        """
        SELECT Moment.* From Moment
        WHERE Moment.issueStatus == :issueStatus
            AND Moment.issueFeedName == :issueFeedName
            AND Moment.issueDate == :issueDate
                    """
    )
    suspend fun get(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): MomentStub?

    suspend fun get(issueOperations: IssueOperations) =
        get(issueOperations.feedName, issueOperations.date, issueOperations.status)
}