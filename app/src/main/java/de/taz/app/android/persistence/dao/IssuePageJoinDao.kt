package de.taz.app.android.persistence.dao

import androidx.room.*
import de.taz.app.android.api.models.IssueBase
import de.taz.app.android.api.models.PageWithoutFile
import de.taz.app.android.persistence.join.IssuePageJoin


@Dao
abstract class IssuePageJoinDao : BaseDao<IssuePageJoin>() {

    @Query(
        """SELECT * FROM Page INNER JOIN IssuePageJoin 
        ON Page.pdfFileName = IssuePageJoin.pageKey 
        WHERE  IssuePageJoin.issueDate == :date AND IssuePageJoin.issueFeedName == :feedName
        """
    )
    abstract fun getPagesForIssue(feedName: String, date: String): List<PageWithoutFile>

    @Query(
        """SELECT * FROM Issue INNER JOIN IssuePageJoin
        ON Issue.feedName = IssuePageJoin.issueFeedName AND Issue.date = IssuePageJoin.issueDate
        WHERE IssuePageJoin.pageKey == :pageKey
    """
    )
    abstract fun getIssueBaseForPage(pageKey: String): IssueBase
}
