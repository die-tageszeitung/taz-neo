package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.SectionStub
import java.util.*

@Dao
interface SectionDao : BaseDao<SectionStub> {

    @Query("SELECT Section.* FROM Section WHERE Section.sectionFileName == :sectionFileName LIMIT 1")
    suspend fun get(sectionFileName: String): SectionStub?

    @Query(
        """SELECT Section.* FROM Section INNER JOIN IssueSectionJoin
        ON Section.sectionFileName == IssueSectionJoin.sectionFileName AND Section.issueDate == IssueSectionJoin.issueDate
        WHERE IssueSectionJoin.issueDate == :issueDate AND IssueSectionJoin.issueFeedName == :issueFeedName
            AND IssueSectionJoin.issueStatus == :issueStatus
        ORDER BY IssueSectionJoin.`index` ASC
    """
    )
    suspend fun getSectionsForIssue(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): List<SectionStub>

    @Query(
        """ SELECT Section.* FROM Section 
        INNER JOIN IssueSectionJoin as ISJ1
        INNER JOIN IssueSectionJoin as ISJ2
        WHERE ISJ1.issueDate == ISJ2.issueDate
        AND ISJ1.issueFeedName == ISJ2.issueFeedName
        AND ISJ1.issueStatus == ISJ2.issueStatus
        AND ISJ2.`index` == ISJ1.`index` - 1
        AND ISJ1.sectionFileName == :sectionFileName
        AND Section.sectionFileName == ISJ2.sectionFileName
    """
    )
    suspend fun getPrevious(sectionFileName: String): SectionStub?


    @Query(
        """ SELECT Section.* FROM Section 
        INNER JOIN IssueSectionJoin as ISJ1
        INNER JOIN IssueSectionJoin as ISJ2
        WHERE ISJ1.issueDate == ISJ2.issueDate
        AND ISJ1.issueFeedName == ISJ2.issueFeedName
        AND ISJ1.issueStatus == ISJ2.issueStatus
        AND ISJ1.sectionFileName == :sectionFileName
        AND ISJ2.`index` == ISJ1.`index` + 1
        AND Section.sectionFileName == ISJ2.sectionFileName
    """
    )
    suspend fun getNext(sectionFileName: String): SectionStub?

    @Query("SELECT dateDownload FROM Section WHERE sectionFileName == :sectionFileName")
    suspend fun getDownloadDate(sectionFileName: String): Date?
}
