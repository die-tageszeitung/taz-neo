package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ResourceInfo")
data class ResourceInfoStub(
    @PrimaryKey val resourceVersion: Int,
    val resourceBaseUrl: String,
    val resourceZip: String,
    val downloadedStatus: DownloadStatus?
) {
    constructor(resourceInfo: ResourceInfo) : this(
        resourceInfo.resourceVersion,
        resourceInfo.resourceBaseUrl,
        resourceInfo.resourceZip,
        resourceInfo.downloadedStatus
    )
}
