package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub

@Entity(
    tableName = "IssueSectionJoin",
    foreignKeys = [
        ForeignKey(
            entity = IssueStub::class,
            parentColumns = ["feedName", "date"],
            childColumns = ["issueFeedName", "issueDate"]
        ),
        ForeignKey(
            entity = SectionStub::class,
            parentColumns = ["sectionFileName"],
            childColumns = ["sectionFileName"]
        )
    ],
    primaryKeys = ["issueFeedName", "issueDate", "sectionFileName"],
    indices = [Index("sectionFileName")]
)
class IssueSectionJoin(
    val issueFeedName: String,
    val issueDate: String,
    val sectionFileName: String,
    val index: Int
)