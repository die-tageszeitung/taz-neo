package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "ResourceInfo")
data class ResourceInfoStub(
    @PrimaryKey val resourceVersion: Int,
    val resourceBaseUrl: String,
    val resourceZip: String,
    val dateDownload: Date?
) {
    constructor(resourceInfo: ResourceInfo) : this(
        resourceInfo.resourceVersion,
        resourceInfo.resourceBaseUrl,
        resourceInfo.resourceZip,
        resourceInfo.dateDownload
    )
}
