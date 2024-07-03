package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.api.models.SectionStub

@Entity(
    tableName = "SectionImageJoin",
    foreignKeys = [
        ForeignKey(
            entity = SectionStub::class,
            parentColumns = ["sectionFileName"],
            childColumns = ["sectionFileName"]
        ),
        ForeignKey(
            entity = ImageStub::class,
            parentColumns = ["fileEntryName"],
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