package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.join.IssueSectionJoin


@Dao
interface IssueSectionJoinDao : BaseDao<IssueSectionJoin> {

    @Query(
        """SELECT Section.sectionFileName FROM Section INNER JOIN IssueSectionJoin
        ON IssueSectionJoin.sectionFileName == Section.sectionFileName
        WHERE  IssueSectionJoin.issueDate == :date AND IssueSectionJoin.issueFeedName == :feedName 
            AND IssueSectionJoin.issueStatus == :status
        ORDER BY IssueSectionJoin.`index` ASC
        """
    )
    suspend fun getSectionNamesForIssue(feedName: String, date: String, status: IssueStatus): List<String>

    suspend fun getSectionNamesForIssue(issueStub: IssueStub) =
        getSectionNamesForIssue(issueStub.feedName, issueStub.date, issueStub.status)

    @Query(
        """ SELECT Issue.* FROM Issue INNER JOIN IssueSectionJoin
            ON IssueSectionJoin.sectionFileName == :sectionName
            AND Issue.feedName == IssueSectionJoin.issueFeedName
            AND Issue.date == IssueSectionJoin.issueDate
            AND Issue.status == IssueSectionJoin.issueStatus
        """
    )
    suspend fun getIssueStubsForSection(sectionName: String): List<IssueStub>

    @Query(
        """ SELECT Issue.* FROM Issue INNER JOIN IssueSectionJoin INNER JOIN SectionArticleJoin
            ON IssueSectionJoin.sectionFileName == SectionArticleJoin.sectionFileName
            AND SectionArticleJoin.articleFileName == :articleFileName
            AND Issue.feedName == IssueSectionJoin.issueFeedName
            AND Issue.date == IssueSectionJoin.issueDate
            AND Issue.status == IssueSectionJoin.issueStatus
        """
    )
    suspend fun getIssueStubsForArticle(articleFileName: String): List<IssueStub>

    @Query(
        """SELECT Section.* FROM Section INNER JOIN IssueSectionJoin
        ON IssueSectionJoin.sectionFileName == Section.sectionFileName
        WHERE  IssueSectionJoin.issueDate == :date AND IssueSectionJoin.issueFeedName == :feedName
            AND IssueSectionJoin.issueStatus == :status
        ORDER BY IssueSectionJoin.`index` ASC LIMIT 1
        """
    )
    suspend fun getFirstSectionForIssue(feedName: String, date: String, status: IssueStatus): SectionStub?

    @Query("DELETE FROM IssueSectionJoin WHERE issueFeedName = :feedName AND issueDate = :date AND issueStatus = :status")
    suspend fun deleteRelationToIssue(feedName: String, date: String, status: IssueStatus)
}
