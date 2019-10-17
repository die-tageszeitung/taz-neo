package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "Download")
data class DownloadWithoutFile(
    @PrimaryKey val fileName: String,
    val baseUrl: String,
    val folder: String,
    var workerManagerId: UUID? = null,
    var status: DownloadStatus = DownloadStatus.pending
) {
    constructor(download: Download) : this(
        download.file.name,
        download.baseUrl,
        download.folder,
        download.workerManagerId,
        download.status
    )
}

