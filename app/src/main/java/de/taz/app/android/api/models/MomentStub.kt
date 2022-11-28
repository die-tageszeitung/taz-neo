package de.taz.app.android.api.models

import androidx.room.Entity
import de.taz.app.android.persistence.repository.IssueKey
import java.util.*

@Entity(
    tableName = "Moment",
    primaryKeys = ["issueFeedName", "issueDate", "issueStatus"]
)
data class MomentStub(
    val issueFeedName: String,
    val issueDate: String,
    val issueStatus: IssueStatus,
    val baseUrl: String,
    val dateDownload: Date?
) {

    constructor(moment: Moment) : this(
        moment.issueFeedName,
        moment.issueDate,
        moment.issueStatus,
        moment.baseUrl,
        moment.dateDownload
    )

    val issueKey
        get() = IssueKey(issueFeedName, issueDate, issueStatus)
}