package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.MomentStub

@Dao
abstract class MomentDao: BaseDao<MomentStub>() {

    @Query(
        """
        SELECT downloadedStatus FROM Moment
        WHERE Moment.issueStatus == :issueStatus
            AND Moment.issueFeedName == :issueFeedName
            AND Moment.issueDate == :issueDate
        """
    )
    abstract fun getDownloadStatus(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): DownloadStatus?

    @Query(
        """
        SELECT Moment.* From Moment
        WHERE Moment.issueStatus == :issueStatus
            AND Moment.issueFeedName == :issueFeedName
            AND Moment.issueDate == :issueDate
                    """
    )
    abstract fun getLiveData(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): LiveData<MomentStub?>

    fun getLiveData(issueOperations: IssueOperations) =
        getLiveData(issueOperations.feedName, issueOperations.date, issueOperations.status)

    @Query(
        """
        SELECT Moment.* From Moment
        WHERE Moment.issueStatus == :issueStatus
            AND Moment.issueFeedName == :issueFeedName
            AND Moment.issueDate == :issueDate
                    """
    )
    abstract fun get(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): MomentStub?

    fun get(issueOperations: IssueOperations) =
        get(issueOperations.feedName, issueOperations.date, issueOperations.status)
}