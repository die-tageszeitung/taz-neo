package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.join.IssueMomentJoin


@Dao
abstract class IssueMomentJoinDao : BaseDao<IssueMomentJoin>() {

    @Query(
        """SELECT * FROM FileEntry INNER JOIN IssueMomentJoin
        ON FileEntry.name == IssueMomentJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == IssueMomentJoin.momentFileName
        WHERE  IssueMomentJoin.issueDate == :date AND IssueMomentJoin.issueFeedName == :feedName
            AND IssueMomentJoin.issueStatus == :status
        ORDER BY IssueMomentJoin.`index` ASC
        """
    )
    abstract fun getMomentFiles(feedName: String, date: String, status: IssueStatus): List<Image>


    @Query(
        """SELECT * FROM FileEntry INNER JOIN IssueMomentJoin
        ON FileEntry.name == IssueMomentJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == IssueMomentJoin.momentFileName
        WHERE  IssueMomentJoin.issueDate == :date AND IssueMomentJoin.issueFeedName == :feedName
            AND IssueMomentJoin.issueStatus == :status
        ORDER BY IssueMomentJoin.`index` ASC
        """
    )
    abstract fun getMomentFilesLiveData(feedName: String, date: String, status: IssueStatus): LiveData<List<Image>>


    @Query(
        """ SELECT Issue.* FROM Issue INNER JOIN IssueMomentJoin
            ON Issue.feedName == IssueMomentJoin.issueFeedName
            AND Issue.date == IssueMomentJoin.issueDate
            WHERE IssueMomentJoin.momentFileName == :momentFileName 
            
        """
    )
    abstract fun getIssueStub(momentFileName: String): IssueStub

    @Query(
        """SELECT * FROM FileEntry INNER JOIN IssueMomentJoin
        ON FileEntry.name == IssueMomentJoin.momentFileName
        INNER JOIN Image ON Image.fileEntryName == IssueMomentJoin.momentFileName
        INNER JOIN IssueMomentJoin as IMJ2 ON IssueMomentJoin.issueDate == IMJ2.issueDate
        AND IssueMomentJoin.issueFeedName == IMJ2.issueFeedName
        AND IssueMomentJoin.issueStatus == IMJ2.issueStatus
        WHERE IMJ2.momentFileName == :imageName
        ORDER BY IssueMomentJoin.`index` ASC
        """
    )
    abstract fun getByImageName(imageName: String): List<Image>

}
