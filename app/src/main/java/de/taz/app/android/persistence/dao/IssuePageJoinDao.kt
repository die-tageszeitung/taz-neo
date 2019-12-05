package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.PageStub
import de.taz.app.android.persistence.join.IssuePageJoin


@Dao
abstract class IssuePageJoinDao : BaseDao<IssuePageJoin>() {

    @Query(
        """SELECT Page.* FROM Page INNER JOIN IssuePageJoin 
        ON Page.pdfFileName = IssuePageJoin.pageKey 
        WHERE  IssuePageJoin.issueDate == :date AND IssuePageJoin.issueFeedName == :feedName
            AND IssuePageJoin.issueStatus == :status
        ORDER BY IssuePageJoin.`index` ASC
        """
    )
    abstract fun getPagesForIssue(feedName: String, date: String, status: IssueStatus): List<PageStub>

    @Query(
        """SELECT Page.pdfFileName FROM Page INNER JOIN IssuePageJoin 
        ON Page.pdfFileName = IssuePageJoin.pageKey 
        WHERE  IssuePageJoin.issueDate == :date AND IssuePageJoin.issueFeedName == :feedName
            AND IssuePageJoin.issueStatus == :status
        ORDER BY IssuePageJoin.`index` ASC
        """
    )
    abstract fun getPageNamesForIssue(feedName: String, date: String, status: IssueStatus): List<String>

    @Query(
        """SELECT Issue.* FROM Issue INNER JOIN IssuePageJoin
        ON Issue.feedName = IssuePageJoin.issueFeedName AND Issue.date = IssuePageJoin.issueDate
        WHERE IssuePageJoin.pageKey == :pageKey
    """
    )
    abstract fun getIssueStubForPage(pageKey: String): IssueStub?
}
