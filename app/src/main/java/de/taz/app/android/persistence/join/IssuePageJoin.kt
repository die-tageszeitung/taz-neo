package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.PageStub

@Entity(
    tableName = "IssuePageJoin",
    foreignKeys = [
        ForeignKey(
            entity = IssueStub::class,
            parentColumns = ["feedName", "date"],
            childColumns = ["issueFeedName", "issueDate"]
        ),
        ForeignKey(
            entity = PageStub::class,
            parentColumns = ["pdfFileName"],
            childColumns = ["pageKey"]
        )
    ],
    primaryKeys = ["issueFeedName", "issueDate", "pageKey"],
    indices = [Index("pageKey")]
)
class IssuePageJoin(
    val issueFeedName: String,
    val issueDate: String,
    val pageKey: String,
    val index: Int
)