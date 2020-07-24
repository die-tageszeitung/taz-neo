package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub

@Entity(
    tableName = "IssueImageMomentJoin",
    foreignKeys = [
        ForeignKey(
            entity = IssueStub::class,
            parentColumns = ["feedName", "date", "status"],
            childColumns = ["issueFeedName", "issueDate", "issueStatus"]
        ),
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["name"],
            childColumns = ["momentFileName"]
        )
    ],
    primaryKeys = ["issueFeedName", "issueDate", "issueStatus", "momentFileName"],
    indices = [Index("momentFileName")]
)
data class IssueImageMomentJoin(
    val issueFeedName: String,
    val issueDate: String,
    val issueStatus: IssueStatus,
    val momentFileName: String,
    val index: Int
)