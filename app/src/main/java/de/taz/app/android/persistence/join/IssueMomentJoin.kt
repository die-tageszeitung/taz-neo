package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.IssueStub

@Entity(
    tableName = "IssueMomentJoin",
    foreignKeys = [
        ForeignKey(
            entity = IssueStub::class,
            parentColumns = ["feedName", "date"],
            childColumns = ["issueFeedName", "issueDate"]
        ),
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["name"],
            childColumns = ["momentFileName"]
        )
    ],
    primaryKeys = ["issueFeedName", "issueDate", "momentFileName"],
    indices = [Index("momentFileName")]
)
data class IssueMomentJoin(
    val issueFeedName: String,
    val issueDate: String,
    val momentFileName: String,
    val index: Int
)