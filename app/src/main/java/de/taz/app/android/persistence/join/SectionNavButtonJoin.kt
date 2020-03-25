package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.Image
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
            entity = Image::class,
            parentColumns = ["name"],
            childColumns = ["navButtonFileName"]
        )
    ],
    primaryKeys = ["sectionFileName", "navButtonFileName"],
    indices = [Index("navButtonFileName")]
)
data class SectionNavButtonJoin(
    val sectionFileName: String,
    val navButtonFileName: String
)