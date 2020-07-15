package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.join.IssueCreditMomentJoin


@Dao
abstract class IssueCreditMomentJoinDao : BaseDao<IssueCreditMomentJoin>() {

    @Query(
        """SELECT  name, storageType, moTime, sha256, size, folder, downloadedStatus, type, alpha, resolution FROM FileEntry INNER JOIN IssueCreditMomentJoin
        ON FileEntry.name == IssueCreditMomentJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == IssueCreditMomentJoin.momentFileName
        WHERE  IssueCreditMomentJoin.issueDate == :date AND IssueCreditMomentJoin.issueFeedName == :feedName
            AND IssueCreditMomentJoin.issueStatus == :status
        ORDER BY IssueCreditMomentJoin.`index` ASC
        """
    )
    abstract fun getMomentFiles(feedName: String, date: String, status: IssueStatus): List<Image>


    @Query(
        """SELECT  name, storageType, moTime, sha256, size, folder, downloadedStatus, type, alpha, resolution FROM FileEntry INNER JOIN IssueCreditMomentJoin
        ON FileEntry.name == IssueCreditMomentJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == IssueCreditMomentJoin.momentFileName
        WHERE  IssueCreditMomentJoin.issueDate == :date AND IssueCreditMomentJoin.issueFeedName == :feedName
            AND IssueCreditMomentJoin.issueStatus == :status
        ORDER BY IssueCreditMomentJoin.`index` ASC
        """
    )
    abstract fun getMomentFilesLiveData(feedName: String, date: String, status: IssueStatus): LiveData<List<Image>>


    @Query(
        """ SELECT Issue.* FROM Issue INNER JOIN IssueCreditMomentJoin
            ON Issue.feedName == IssueCreditMomentJoin.issueFeedName
            AND Issue.date == IssueCreditMomentJoin.issueDate
            WHERE IssueCreditMomentJoin.momentFileName == :momentFileName 
            
        """
    )
    abstract fun getIssueStub(momentFileName: String): IssueStub

    @Query(
        """SELECT name, storageType, moTime, sha256, size, folder, downloadedStatus, type, alpha, resolution FROM FileEntry INNER JOIN IssueCreditMomentJoin
        ON FileEntry.name == IssueCreditMomentJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == IssueCreditMomentJoin.momentFileName
        INNER JOIN IssueCreditMomentJoin as IMJ2 ON IssueCreditMomentJoin.issueDate == IMJ2.issueDate
        AND IssueCreditMomentJoin.issueFeedName == IMJ2.issueFeedName
        AND IssueCreditMomentJoin.issueStatus == IMJ2.issueStatus
        WHERE IMJ2.momentFileName == :imageName
        ORDER BY IssueCreditMomentJoin.`index` ASC
        """
    )
    abstract fun getByImageName(imageName: String): List<Image>

}
