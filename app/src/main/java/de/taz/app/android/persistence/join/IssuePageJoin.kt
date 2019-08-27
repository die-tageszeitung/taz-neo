package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import de.taz.app.android.api.models.*

@Entity(tableName = "IssuePage",
    foreignKeys = [
        ForeignKey(entity = IssueBase::class,
            parentColumns = ["feedName", "date"],
            childColumns = ["issueFeedName", "issueDate"]),
        ForeignKey(entity = PageWithoutFile::class,
            parentColumns = ["pdfFileName"],
            childColumns = ["pageKey"])
    ],
    primaryKeys = ["issueFeedName", "issueDate", "pageKey"]
)
class IssuePageJoin(
    val issueFeedName: String,
    val issueDate: String,
    val pageKey: String
)