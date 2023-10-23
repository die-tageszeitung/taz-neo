package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.join.MomentImageJoin


@Dao
interface MomentImageJoinDao : BaseDao<MomentImageJoin> {

    @Query(
        """SELECT  name, storageType, moTime, sha256, size, folder, type, alpha, resolution, dateDownload, path, storageLocation FROM FileEntry INNER JOIN MomentImageJoin
        ON FileEntry.name == MomentImageJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == MomentImageJoin.momentFileName
        WHERE  MomentImageJoin.issueDate == :date AND MomentImageJoin.issueFeedName == :feedName
            AND MomentImageJoin.issueStatus == :status
        ORDER BY MomentImageJoin.`index` ASC
        """
    )
    suspend fun getMomentFiles(feedName: String, date: String, status: IssueStatus): List<Image>

    @Query(
        """ SELECT Issue.* FROM Issue INNER JOIN MomentImageJoin
            ON Issue.feedName == MomentImageJoin.issueFeedName
            AND Issue.date == MomentImageJoin.issueDate
            WHERE MomentImageJoin.momentFileName == :momentFileName 
            
        """
    )
    suspend fun getIssueStub(momentFileName: String): IssueStub?


    /**
     * Delete all the entries related to the given issue from this join table.
     * Note: this does not delete any data in the related Image, Moment or FileEntry table.
     */
    @Query("DELETE FROM MomentImageJoin WHERE issueFeedName = :feedName AND issueDate = :date AND issueStatus = :status")
    suspend fun delete(feedName: String, date: String, status: IssueStatus)
}
