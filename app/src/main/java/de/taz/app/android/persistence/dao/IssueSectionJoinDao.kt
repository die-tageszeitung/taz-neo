package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueBase
import de.taz.app.android.api.models.SectionBase
import de.taz.app.android.persistence.join.IssueSectionJoin


@Dao
abstract class IssueSectionJoinDao : BaseDao<IssueSectionJoin>() {

    @Query(
        """SELECT Section.* FROM Section INNER JOIN IssueSectionJoin
        ON IssueSectionJoin.sectionFileName == Section.sectionFileName
        WHERE  IssueSectionJoin.issueDate == :date AND IssueSectionJoin.issueFeedName == :feedName
        """
    )
    abstract fun getSectionsForIssue(feedName: String, date: String): List<SectionBase>

    @Query(
        """SELECT Section.sectionFileName FROM Section INNER JOIN IssueSectionJoin
        ON IssueSectionJoin.sectionFileName == Section.sectionFileName
        WHERE  IssueSectionJoin.issueDate == :date AND IssueSectionJoin.issueFeedName == :feedName
        """
    )
    abstract fun getSectionNamesForIssue(feedName: String, date: String): List<String>

    fun getSectionNamesForIssue(issueBase: IssueBase) =
        getSectionNamesForIssue(issueBase.feedName, issueBase.date)
}
