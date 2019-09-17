package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.SectionBase

@Entity(
    tableName = "SectionImageJoin",
    foreignKeys = [
        ForeignKey(
            entity = SectionBase::class,
            parentColumns = ["sectionFileName"],
            childColumns = ["sectionFileName"]
        ),
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["name"],
            childColumns = ["imageFileName"]
        )
    ],
    primaryKeys = ["sectionFileName", "imageFileName"],
    indices = [Index("imageFileName")]
)
data class SectionImageJoin(
    val sectionFileName: String,
    val imageFileName: String,
    val index: Int
)