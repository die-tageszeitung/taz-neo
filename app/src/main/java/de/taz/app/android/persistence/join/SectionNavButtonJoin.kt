package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.api.models.SectionStub

@Entity(
    tableName = "SectionNavButtonJoin",
    foreignKeys = [
        ForeignKey(
            entity = SectionStub::class,
            parentColumns = ["sectionFileName"],
            childColumns = ["sectionFileName"]
        ),
        ForeignKey(
            entity = ImageStub::class,
            parentColumns = ["fileEntryName"],
            childColumns = ["navButtonFileName"]
        )
    ],
    primaryKeys = ["sectionFileName", "navButtonFileName"],
    indices = [Index("sectionFileName"), Index("navButtonFileName")]
)
data class SectionNavButtonJoin(
    val sectionFileName: String,
    val navButtonFileName: String
)