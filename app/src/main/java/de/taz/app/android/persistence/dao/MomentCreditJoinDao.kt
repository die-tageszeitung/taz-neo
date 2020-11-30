package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.join.MomentCreditJoin


@Dao
abstract class MomentCreditJoinDao : BaseDao<MomentCreditJoin>() {

    @Query(
        """SELECT  name, storageType, moTime, sha256, size, folder, type, alpha, resolution, dateDownload FROM FileEntry INNER JOIN MomentCreditJoin
        ON FileEntry.name == MomentCreditJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == MomentCreditJoin.momentFileName
        WHERE  MomentCreditJoin.issueDate == :date AND MomentCreditJoin.issueFeedName == :feedName
            AND MomentCreditJoin.issueStatus == :status
        ORDER BY MomentCreditJoin.`index` ASC
        """
    )
    abstract fun getMomentFiles(feedName: String, date: String, status: IssueStatus): List<Image>


    @Query(
        """SELECT  name, storageType, moTime, sha256, size, folder, type, alpha, resolution, dateDownload FROM FileEntry INNER JOIN MomentCreditJoin
        ON FileEntry.name == MomentCreditJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == MomentCreditJoin.momentFileName
        WHERE  MomentCreditJoin.issueDate == :date AND MomentCreditJoin.issueFeedName == :feedName
            AND MomentCreditJoin.issueStatus == :status
        ORDER BY MomentCreditJoin.`index` ASC
        """
    )
    abstract fun getMomentFilesLiveData(feedName: String, date: String, status: IssueStatus): LiveData<List<Image>>


    @Query(
        """ SELECT Issue.* FROM Issue INNER JOIN MomentCreditJoin
            ON Issue.feedName == MomentCreditJoin.issueFeedName
            AND Issue.date == MomentCreditJoin.issueDate
            WHERE MomentCreditJoin.momentFileName == :momentFileName 
            
        """
    )
    abstract fun getIssueStub(momentFileName: String): IssueStub

    @Query(
        """SELECT name, storageType, moTime, sha256, size, folder, type, alpha, resolution, dateDownload FROM FileEntry INNER JOIN MomentCreditJoin
        ON FileEntry.name == MomentCreditJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == MomentCreditJoin.momentFileName
        INNER JOIN MomentCreditJoin as IMJ2 ON MomentCreditJoin.issueDate == IMJ2.issueDate
        AND MomentCreditJoin.issueFeedName == IMJ2.issueFeedName
        AND MomentCreditJoin.issueStatus == IMJ2.issueStatus
        WHERE IMJ2.momentFileName == :imageName
        ORDER BY MomentCreditJoin.`index` ASC
        """
    )
    abstract fun getByImageName(imageName: String): List<Image>

}
