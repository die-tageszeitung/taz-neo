package de.taz.app.android.api.models

import androidx.room.Entity

@Entity(
    tableName = "Moment",
    primaryKeys = ["issueFeedName", "issueDate", "issueStatus"]
)
data class MomentStub(
    val issueFeedName: String,
    val issueDate: String,
    val issueStatus: IssueStatus,
    val downloadedStatus: DownloadStatus?
) {

    constructor(moment: Moment) : this(
        moment.issueFeedName,
        moment.issueDate,
        moment.issueStatus,
        moment.downloadedStatus
    )

}