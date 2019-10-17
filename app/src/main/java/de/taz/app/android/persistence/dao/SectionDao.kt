package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.SectionBase

@Dao
abstract class SectionDao : BaseDao<SectionBase>() {

    @Query("SELECT Section.* FROM Section WHERE Section.sectionFileName == :sectionFileName LIMIT 1")
    abstract fun get(sectionFileName: String): SectionBase

    @Query(""" SELECT Section.* FROM Section 
        INNER JOIN IssueSectionJoin as ISJ1
        INNER JOIN IssueSectionJoin as ISJ2
        WHERE ISJ1.issueDate == ISJ2.issueDate
        AND ISJ1.issueFeedName == ISJ2.issueFeedName
        AND ISJ2.`index` == ISJ1.`index` - 1
        AND ISJ1.sectionFileName == :sectionFileName
        AND Section.sectionFileName == ISJ2.sectionFileName
    """)
    abstract fun getPrevious(sectionFileName: String): SectionBase?


    @Query(""" SELECT Section.* FROM Section 
        INNER JOIN IssueSectionJoin as ISJ1
        INNER JOIN IssueSectionJoin as ISJ2
        WHERE ISJ1.issueDate == ISJ2.issueDate
        AND ISJ1.issueFeedName == ISJ2.issueFeedName
        AND ISJ1.sectionFileName == :sectionFileName
        AND ISJ2.`index` == ISJ1.`index` + 1
        AND Section.sectionFileName == ISJ2.sectionFileName
    """)
    abstract fun getNext(sectionFileName: String): SectionBase?

}
