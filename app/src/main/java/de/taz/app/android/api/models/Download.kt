package de.taz.app.android.api.models

import java.util.*

data class Download(
    val baseUrl: String,
    val file: FileEntry,
    var status: DownloadStatus = DownloadStatus.pending,
    var workerManagerId: UUID? = null,
    val tag: String? = null
){
    constructor(downloadStub: DownloadStub, file: FileEntry, tag: String? = null): this(
        downloadStub.baseUrl,
        file,
        downloadStub.status,
        downloadStub.workerManagerId,
        tag
    )

    val url
        get() = "$baseUrl/${file.name}"

}

enum class DownloadStatus {
    aborted,
    done,
    started,
    pending
}