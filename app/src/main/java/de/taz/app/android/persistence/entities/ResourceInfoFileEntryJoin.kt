package de.taz.app.android.persistence.entities

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(tableName = "ResourceInfoFileEntry",
    foreignKeys = [
        ForeignKey(entity = ResourceInfoEntity::class,
            parentColumns = ["resourceVersion"],
            childColumns = ["resourceInfoVersion"]),
        ForeignKey(entity = FileEntryEntity::class,
            parentColumns = ["name"],
            childColumns = ["fileEntryName"])
    ],
    primaryKeys = ["resourceInfoVersion", "fileEntryName"]
)
class ResourceInfoFileEntryJoin(
    val resourceInfoVersion: Int,
    val fileEntryName: String
)