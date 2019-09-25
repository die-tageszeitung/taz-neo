package de.taz.app.android.persistence.join

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.ResourceInfoWithoutFiles

@Entity(
    tableName = "ResourceInfoFileEntryJoin",
    foreignKeys = [
        ForeignKey(
            entity = ResourceInfoWithoutFiles::class,
            parentColumns = ["resourceVersion"],
            childColumns = ["resourceInfoVersion"]
        ),
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["name"],
            childColumns = ["fileEntryName"]
        )
    ],
    primaryKeys = ["resourceInfoVersion", "fileEntryName"],
    indices = [Index("fileEntryName")]
)
class ResourceInfoFileEntryJoin(
    val resourceInfoVersion: Int,
    val fileEntryName: String,
    val index: Int
)