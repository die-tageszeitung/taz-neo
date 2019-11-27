package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.join.IssueSectionJoin


@Dao
abstract class IssueSectionJoinDao : BaseDao<IssueSectionJoin>() {

    @Query(
        """SELECT Section.* FROM Section INNER JOIN IssueSectionJoin
        ON IssueSectionJoin.sectionFileName == Section.sectionFileName
        WHERE  IssueSectionJoin.issueDate == :date AND IssueSectionJoin.issueFeedName == :feedName
        ORDER BY IssueSectionJoin.`index` ASC
        """
    )
    abstract fun getSectionsForIssue(feedName: String, date: String): List<SectionStub>

    @Query(
        """SELECT Section.sectionFileName FROM Section INNER JOIN IssueSectionJoin
        ON IssueSectionJoin.sectionFileName == Section.sectionFileName
        WHERE  IssueSectionJoin.issueDate == :date AND IssueSectionJoin.issueFeedName == :feedName
        ORDER BY IssueSectionJoin.`index` ASC
        """
    )
    abstract fun getSectionNamesForIssue(feedName: String, date: String): List<String>

    fun getSectionNamesForIssue(issueStub: IssueStub) =
        getSectionNamesForIssue(issueStub.feedName, issueStub.date)

    @Query(
        """ SELECT Issue.* FROM Issue INNER JOIN IssueSectionJoin
            ON IssueSectionJoin.sectionFileName == :sectionName
            AND Issue.feedName == IssueSectionJoin.issueFeedName
            AND Issue.date == IssueSectionJoin.issueDate
        """
    )
    abstract fun getIssueStubForSection(sectionName: String): IssueStub
    @Query(
        """SELECT Section.* FROM Section INNER JOIN IssueSectionJoin
        ON IssueSectionJoin.sectionFileName == Section.sectionFileName
        WHERE  IssueSectionJoin.issueDate == :date AND IssueSectionJoin.issueFeedName == :feedName
        ORDER BY IssueSectionJoin.`index` ASC LIMIT 1
        """
    )
    abstract fun getFirstSectionForIssue(feedName: String, date: String): SectionStub

}
