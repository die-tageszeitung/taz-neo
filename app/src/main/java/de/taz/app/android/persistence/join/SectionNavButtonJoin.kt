package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.dto.StorageType
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
            parentColumns = ["name", "storageType"],
            childColumns = ["navButtonFileName", "navButtonStorageType"]
        )
    ],
    primaryKeys = ["sectionFileName", "navButtonFileName", "navButtonStorageType"],
    indices = [Index("sectionFileName")]
)
data class SectionNavButtonJoin(
    val sectionFileName: String,
    val navButtonFileName: String,
    val navButtonStorageType: StorageType
)