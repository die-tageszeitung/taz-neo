package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.join.MomentImageJoin


@Dao
abstract class MomentImageJoinDao : BaseDao<MomentImageJoin>() {

    @Query(
        """SELECT  name, storageType, moTime, sha256, size, folder, type, alpha, resolution FROM FileEntry INNER JOIN MomentImageJoin
        ON FileEntry.name == MomentImageJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == MomentImageJoin.momentFileName
        WHERE  MomentImageJoin.issueDate == :date AND MomentImageJoin.issueFeedName == :feedName
            AND MomentImageJoin.issueStatus == :status
        ORDER BY MomentImageJoin.`index` ASC
        """
    )
    abstract fun getMomentFiles(feedName: String, date: String, status: IssueStatus): List<Image>


    @Query(
        """SELECT  name, storageType, moTime, sha256, size, folder, type, alpha, resolution FROM FileEntry INNER JOIN MomentImageJoin
        ON FileEntry.name == MomentImageJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == MomentImageJoin.momentFileName
        WHERE  MomentImageJoin.issueDate == :date AND MomentImageJoin.issueFeedName == :feedName
            AND MomentImageJoin.issueStatus == :status
        ORDER BY MomentImageJoin.`index` ASC
        """
    )
    abstract fun getMomentFilesLiveData(
        feedName: String,
        date: String,
        status: IssueStatus
    ): LiveData<List<Image>>


    @Query(
        """ SELECT Issue.* FROM Issue INNER JOIN MomentImageJoin
            ON Issue.feedName == MomentImageJoin.issueFeedName
            AND Issue.date == MomentImageJoin.issueDate
            WHERE MomentImageJoin.momentFileName == :momentFileName 
            
        """
    )
    abstract fun getIssueStub(momentFileName: String): IssueStub

    @Query(
        """SELECT  name, storageType, moTime, sha256, size, folder, type, alpha, resolution 
        FROM FileEntry INNER JOIN MomentImageJoin
        ON FileEntry.name == MomentImageJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == MomentImageJoin.momentFileName
        INNER JOIN MomentImageJoin as IMJ2 ON MomentImageJoin.issueDate == IMJ2.issueDate
        AND MomentImageJoin.issueFeedName == IMJ2.issueFeedName
        AND MomentImageJoin.issueStatus == IMJ2.issueStatus
        WHERE IMJ2.momentFileName == :imageName
        ORDER BY MomentImageJoin.`index` ASC
        """
    )
    abstract fun getByImageName(imageName: String): List<Image>

}
