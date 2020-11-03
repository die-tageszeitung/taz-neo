package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.persistence.join.IssueFilesMomentJoin


@Dao
abstract class IssueFilesMomentJoinDao : BaseDao<IssueFilesMomentJoin>() {

    @Query("""SELECT FileEntry.* FROM FileEntry INNER JOIN IssueFilesMomentJoin
        ON FileEntry.name = IssueFilesMomentJoin.momentFileName 
        WHERE IssueFilesMomentJoin.issueDate = :date AND IssueFilesMomentJoin.issueFeedName == :feedName
            AND IssueFilesMomentJoin.issueStatus == :status
        ORDER BY IssueFilesMomentJoin.`index` ASC
    """)
    abstract fun getMomentFiles(feedName: String, date: String, status: IssueStatus): List<FileEntry>


    @Query(
        """SELECT  FileEntry.* FROM FileEntry INNER JOIN IssueFilesMomentJoin
        ON FileEntry.name == IssueFilesMomentJoin.momentFileName
        WHERE  IssueFilesMomentJoin.issueDate == :date AND IssueFilesMomentJoin.issueFeedName == :feedName
            AND IssueFilesMomentJoin.issueStatus == :status
        ORDER BY IssueFilesMomentJoin.`index` ASC
        """
    )
    abstract fun getMomentFilesLiveData(feedName: String, date: String, status: IssueStatus): LiveData<List<FileEntry>>

}
