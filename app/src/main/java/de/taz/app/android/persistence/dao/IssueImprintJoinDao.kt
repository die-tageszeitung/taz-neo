package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.persistence.join.IssueImprintJoin


@Dao
abstract class IssueImprintJoinDao : BaseDao<IssueImprintJoin>() {

    @Query(
        """SELECT Article.articleFileName FROM Article INNER JOIN IssueImprintJoin
        ON Article.articleFileName == IssueImprintJoin.articleFileName
        WHERE  IssueImprintJoin.issueDate == :date AND IssueImprintJoin.issueFeedName == :feedName
        """
    )
    abstract fun getImprintNameForIssue(feedName: String, date: String): String?

}
