package de.taz.app.android.api.models

import java.util.*

data class Download(
    val baseUrl: String,
    val folder: String,
    val file: FileEntry,
    var status: DownloadStatus = DownloadStatus.pending,
    var workerManagerId: UUID? = null,
    val tag: String? = null
){
    constructor(downloadWithoutFile: DownloadWithoutFile, file: FileEntry, tag: String? = null): this(
        downloadWithoutFile.baseUrl,
        downloadWithoutFile.folder,
        file,
        downloadWithoutFile.status,
        downloadWithoutFile.workerManagerId,
        tag
    )

    val url
        get() = "$baseUrl/${file.name}"

    val path
        get() = "$folder/${file.name}"

}

enum class DownloadStatus {
    aborted,
    done,
    started,
    pending
}