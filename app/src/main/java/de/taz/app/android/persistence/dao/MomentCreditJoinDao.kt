package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.join.MomentCreditJoin


@Dao
interface MomentCreditJoinDao : BaseDao<MomentCreditJoin> {

    @Query(
        """SELECT  name, storageType, moTime, sha256, size, folder, type, alpha, resolution, dateDownload, path, storageLocation FROM FileEntry INNER JOIN MomentCreditJoin
        ON FileEntry.name == MomentCreditJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == MomentCreditJoin.momentFileName
        WHERE  MomentCreditJoin.issueDate == :date AND MomentCreditJoin.issueFeedName == :feedName
            AND MomentCreditJoin.issueStatus == :status
        ORDER BY MomentCreditJoin.`index` ASC
        """
    )
    suspend fun getMomentFiles(feedName: String, date: String, status: IssueStatus): List<Image>

    /**
     * Delete all the entries related to the given issue from this join table.
     * Note: this does not delete any data in the related Moment or FileEntry table.
     */
    @Query("DELETE FROM MomentCreditJoin WHERE issueFeedName = :feedName AND issueDate = :date AND issueStatus = :status")
    suspend fun deleteRelationToMoment(feedName: String, date: String, status: IssueStatus)
}
