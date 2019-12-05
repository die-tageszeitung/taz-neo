package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub

@Entity(
    tableName = "IssueImprintJoin",
    foreignKeys = [
        ForeignKey(
            entity = IssueStub::class,
            parentColumns = ["feedName", "date", "status"],
            childColumns = ["issueFeedName", "issueDate", "issueStatus"]
        ),
        ForeignKey(
            entity = ArticleStub::class,
            parentColumns = ["articleFileName"],
            childColumns = ["articleFileName"]
        )
    ],
    primaryKeys = ["issueFeedName", "issueDate", "issueStatus", "articleFileName"],
    indices = [Index("articleFileName")]
)
data class IssueImprintJoin(
    val issueFeedName: String,
    val issueDate: String,
    val issueStatus: IssueStatus,
    val articleFileName: String
)