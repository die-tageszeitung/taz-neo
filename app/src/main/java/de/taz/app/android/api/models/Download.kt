package de.taz.app.android.api.models

import de.taz.app.android.api.interfaces.DownloadOperations
import java.util.*

data class Download(
    override val baseUrl: String,
    val file: FileEntry,
    override var status: DownloadStatus = DownloadStatus.pending,
    val tag: String? = null,
    var lastSha256: String? = null
): DownloadOperations {
    constructor(downloadStub: DownloadStub, file: FileEntry, tag: String? = null): this(
        baseUrl = downloadStub.baseUrl,
        file = file,
        status = downloadStub.status,
        tag = tag,
        lastSha256 = downloadStub.lastSha256
    )

    override val fileName: String
        get() = file.name

}

enum class DownloadStatus {
    aborted,
    done,
    started,
    pending,
    takeOld,
    failed
}