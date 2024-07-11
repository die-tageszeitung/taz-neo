package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.MomentStub

@Entity(
    tableName = "MomentCreditJoin",
    foreignKeys = [
        ForeignKey(
            entity = MomentStub::class,
            parentColumns = ["issueFeedName", "issueDate", "issueStatus"],
            childColumns = ["issueFeedName", "issueDate", "issueStatus"]
        ),
        ForeignKey(
            entity = ImageStub::class,
            parentColumns = ["fileEntryName"],
            childColumns = ["momentFileName"]
        )
    ],
    primaryKeys = ["issueFeedName", "issueDate", "issueStatus", "momentFileName"],
    indices = [Index("momentFileName")]
)
data class MomentCreditJoin(
    val issueFeedName: String,
    val issueDate: String,
    val issueStatus: IssueStatus,
    val momentFileName: String,
    val index: Int
)