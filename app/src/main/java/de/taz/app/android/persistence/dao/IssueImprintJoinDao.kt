package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.join.IssueImprintJoin


@Dao
abstract class IssueImprintJoinDao : BaseDao<IssueImprintJoin>() {

    @Query(
        """SELECT Article.articleFileName FROM Article INNER JOIN IssueImprintJoin
        ON Article.articleFileName == IssueImprintJoin.articleFileName
        WHERE  IssueImprintJoin.issueDate == :date AND IssueImprintJoin.issueFeedName == :feedName
            AND IssueImprintJoin.issueStatus == :status
        """
    )
    abstract fun getImprintNameForIssue(feedName: String, date: String, status: IssueStatus): String?

    @Query(
        """SELECT Issue.* FROM Issue INNER JOIN IssueImprintJoin
        ON IssueImprintJoin.articleFileName == :imprintFileName
        """
    )
    abstract fun getIssueForImprintFileName(imprintFileName: String): IssueStub?

}
