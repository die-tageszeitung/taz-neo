package de.taz.app.android.persistence.dao

import androidx.room.*
import de.taz.app.android.api.models.IssueBase
import de.taz.app.android.api.models.PageWithoutFile
import de.taz.app.android.persistence.join.IssuePageJoin


@Dao
abstract class IssuePageJoinDao : BaseDao<IssuePageJoin>() {

    @Query(
        """SELECT * FROM Page INNER JOIN IssuePage 
        ON Page.pdfFileName = IssuePage.pageKey 
        WHERE  IssuePage.issueDate == :date AND IssuePage.issueFeedName == :feedName
        """
    )
    abstract fun getPagesForIssue(feedName: String, date: String): List<PageWithoutFile>

    @Query(
        """SELECT * FROM Issue INNER JOIN IssuePage
        ON Issue.feedName = IssuePage.issueFeedName AND Issue.date = IssuePage.issueDate
        WHERE IssuePage.pageKey == :pageKey
    """
    )
    abstract fun getIssueBaseForPage(pageKey: String): IssueBase
}
