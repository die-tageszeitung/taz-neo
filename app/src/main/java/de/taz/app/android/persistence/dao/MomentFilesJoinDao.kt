package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.persistence.join.MomentFilesJoin


@Dao
abstract class MomentFilesJoinDao : BaseDao<MomentFilesJoin>() {

    @Query("""SELECT FileEntry.* FROM FileEntry INNER JOIN MomentFilesJoin
        ON FileEntry.name = MomentFilesJoin.momentFileName 
        WHERE MomentFilesJoin.issueDate = :date AND MomentFilesJoin.issueFeedName == :feedName
            AND MomentFilesJoin.issueStatus == :status
        ORDER BY MomentFilesJoin.`index` ASC
    """)
    abstract fun getMomentFiles(feedName: String, date: String, status: IssueStatus): List<FileEntry>


    @Query(
        """SELECT  FileEntry.* FROM FileEntry INNER JOIN MomentFilesJoin
        ON FileEntry.name == MomentFilesJoin.momentFileName
        WHERE  MomentFilesJoin.issueDate == :date AND MomentFilesJoin.issueFeedName == :feedName
            AND MomentFilesJoin.issueStatus == :status
        ORDER BY MomentFilesJoin.`index` ASC
        """
    )
    abstract fun getMomentFilesLiveData(feedName: String, date: String, status: IssueStatus): LiveData<List<FileEntry>>

}
