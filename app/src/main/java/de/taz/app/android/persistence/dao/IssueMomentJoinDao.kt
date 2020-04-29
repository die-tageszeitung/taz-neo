package de.taz.app.android.persistence.dao

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
        WHERE  IssueMomentJoin.momentFileName == :imageName
        ORDER BY IssueMomentJoin.`index` ASC
        """
    )
    abstract fun getByImageName(imageName: String): List<Image>

}
