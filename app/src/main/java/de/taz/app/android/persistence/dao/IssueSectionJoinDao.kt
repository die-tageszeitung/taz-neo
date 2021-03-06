package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.join.IssueSectionJoin


@Dao
abstract class IssueSectionJoinDao : BaseDao<IssueSectionJoin>() {

    @Query(
        """SELECT Section.* FROM Section INNER JOIN IssueSectionJoin
        ON IssueSectionJoin.sectionFileName == Section.sectionFileName
        WHERE  IssueSectionJoin.issueDate == :date AND IssueSectionJoin.issueFeedName == :feedName
            AND IssueSectionJoin.issueStatus == :status
        ORDER BY IssueSectionJoin.`index` ASC
        """
    )
    abstract fun getSectionsForIssue(feedName: String, date: String, status: IssueStatus): List<SectionStub>

    @Query(
        """SELECT Section.sectionFileName FROM Section INNER JOIN IssueSectionJoin
        ON IssueSectionJoin.sectionFileName == Section.sectionFileName
        WHERE  IssueSectionJoin.issueDate == :date AND IssueSectionJoin.issueFeedName == :feedName 
            AND IssueSectionJoin.issueStatus == :status
        ORDER BY IssueSectionJoin.`index` ASC
        """
    )
    abstract fun getSectionNamesForIssue(feedName: String, date: String, status: IssueStatus): List<String>

    fun getSectionNamesForIssue(issueStub: IssueStub) =
        getSectionNamesForIssue(issueStub.feedName, issueStub.date, issueStub.status)

    @Query(
        """ SELECT Issue.* FROM Issue INNER JOIN IssueSectionJoin
            ON IssueSectionJoin.sectionFileName == :sectionName
            AND Issue.feedName == IssueSectionJoin.issueFeedName
            AND Issue.date == IssueSectionJoin.issueDate
            AND Issue.status == IssueSectionJoin.issueStatus
        """
    )
    abstract fun getIssueStubForSection(sectionName: String): IssueStub?

    @Query(
        """ SELECT Issue.* FROM Issue INNER JOIN IssueSectionJoin INNER JOIN SectionArticleJoin
            ON IssueSectionJoin.sectionFileName == SectionArticleJoin.sectionFileName
            AND SectionArticleJoin.articleFileName == :articleFileName
            AND Issue.feedName == IssueSectionJoin.issueFeedName
            AND Issue.date == IssueSectionJoin.issueDate
            AND Issue.status == IssueSectionJoin.issueStatus
        """
    )
    abstract fun getIssueStubForArticle(articleFileName: String): IssueStub?

    @Query(
        """SELECT Section.* FROM Section INNER JOIN IssueSectionJoin
        ON IssueSectionJoin.sectionFileName == Section.sectionFileName
        WHERE  IssueSectionJoin.issueDate == :date AND IssueSectionJoin.issueFeedName == :feedName
            AND IssueSectionJoin.issueStatus == :status
        ORDER BY IssueSectionJoin.`index` ASC LIMIT 1
        """
    )
    abstract fun getFirstSectionForIssue(feedName: String, date: String, status: IssueStatus): SectionStub

}
