package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.DownloadOperations

@Entity(tableName = "Download")
data class DownloadStub(
    @PrimaryKey override val fileName: String,
    override val baseUrl: String,
    override var status: DownloadStatus = DownloadStatus.pending,
    var lastSha256: String? = null
) : DownloadOperations {
    constructor(download: Download) : this(
        download.file.name,
        download.baseUrl,
        download.status
    )

}

