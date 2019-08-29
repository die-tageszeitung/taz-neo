package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import de.taz.app.android.api.models.ArticleBase
import de.taz.app.android.api.models.IssueBase

@Entity(
    tableName = "IssueImprintJoin",
    foreignKeys = [
        ForeignKey(
            entity = IssueBase::class,
            parentColumns = ["feedName", "date"],
            childColumns = ["issueFeedName", "issueDate"]
        ),
        ForeignKey(
            entity = ArticleBase::class,
            parentColumns = ["articleFileName"],
            childColumns = ["articleFileName"]
        )
    ],
    primaryKeys = ["issueFeedName", "issueDate", "articleFileName"]
)
data class IssueImprintJoin(
    val issueFeedName: String,
    val issueDate: String,
    val articleFileName: String
)