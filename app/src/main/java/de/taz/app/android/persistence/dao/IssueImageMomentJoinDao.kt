package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.join.IssueImageMomentJoin


@Dao
abstract class IssueImageMomentJoinDao : BaseDao<IssueImageMomentJoin>() {

    @Query(
        """SELECT  name, storageType, moTime, sha256, size, folder, downloadedStatus, type, alpha, resolution FROM FileEntry INNER JOIN IssueImageMomentJoin
        ON FileEntry.name == IssueImageMomentJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == IssueImageMomentJoin.momentFileName
        WHERE  IssueImageMomentJoin.issueDate == :date AND IssueImageMomentJoin.issueFeedName == :feedName
            AND IssueImageMomentJoin.issueStatus == :status
        ORDER BY IssueImageMomentJoin.`index` ASC
        """
    )
    abstract fun getMomentFiles(feedName: String, date: String, status: IssueStatus): List<Image>


    @Query(
        """SELECT  name, storageType, moTime, sha256, size, folder, downloadedStatus, type, alpha, resolution FROM FileEntry INNER JOIN IssueImageMomentJoin
        ON FileEntry.name == IssueImageMomentJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == IssueImageMomentJoin.momentFileName
        WHERE  IssueImageMomentJoin.issueDate == :date AND IssueImageMomentJoin.issueFeedName == :feedName
            AND IssueImageMomentJoin.issueStatus == :status
        ORDER BY IssueImageMomentJoin.`index` ASC
        """
    )
    abstract fun getMomentFilesLiveData(
        feedName: String,
        date: String,
        status: IssueStatus
    ): LiveData<List<Image>>


    @Query(
        """ SELECT Issue.* FROM Issue INNER JOIN IssueImageMomentJoin
            ON Issue.feedName == IssueImageMomentJoin.issueFeedName
            AND Issue.date == IssueImageMomentJoin.issueDate
            WHERE IssueImageMomentJoin.momentFileName == :momentFileName 
            
        """
    )
    abstract fun getIssueStub(momentFileName: String): IssueStub

    @Query(
        """SELECT  name, storageType, moTime, sha256, size, folder, downloadedStatus, type, alpha, resolution 
        FROM FileEntry INNER JOIN IssueImageMomentJoin
        ON FileEntry.name == IssueImageMomentJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == IssueImageMomentJoin.momentFileName
        INNER JOIN IssueImageMomentJoin as IMJ2 ON IssueImageMomentJoin.issueDate == IMJ2.issueDate
        AND IssueImageMomentJoin.issueFeedName == IMJ2.issueFeedName
        AND IssueImageMomentJoin.issueStatus == IMJ2.issueStatus
        WHERE IMJ2.momentFileName == :imageName
        ORDER BY IssueImageMomentJoin.`index` ASC
        """
    )
    abstract fun getByImageName(imageName: String): List<Image>

}
