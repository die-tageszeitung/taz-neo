package de.taz.app.android.persistence.dao

import androidx.room.*
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleBase
import de.taz.app.android.persistence.join.IssueImprintJoin


@Dao
abstract class IssueImprintJoinDao : BaseDao<IssueImprintJoin>() {

    @Query(
        """SELECT articleFileName FROM Article INNER JOIN IssueImprintJoin
        ON Article.articleFileName == IssueImprintJoin.articleFileName
        WHERE  IssueImprintJoin.issueDate == :date AND IssueImprintJoin.issueFeedName == :feedName
        """
    )
    abstract fun getImprintNameForIssue(feedName: String, date: String): String

}
