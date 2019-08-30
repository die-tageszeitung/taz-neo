package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import de.taz.app.android.api.models.*

@Entity(
    tableName = "IssueSectionJoin",
    foreignKeys = [
        ForeignKey(
            entity = IssueBase::class,
            parentColumns = ["feedName", "date"],
            childColumns = ["issueFeedName", "issueDate"]
        ),
        ForeignKey(
            entity = SectionBase::class,
            parentColumns = ["sectionFileName"],
            childColumns = ["sectionFileName"]
        )
    ],
    primaryKeys = ["issueFeedName", "issueDate", "sectionFileName"]
)
class IssueSectionJoin(
    val issueFeedName: String,
    val issueDate: String,
    val sectionFileName: String
)