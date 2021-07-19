package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.DownloadStatus

interface DownloadOperations {

    val fileName: String
    val baseUrl: String
    var status: DownloadStatus

    val url: String
        get() = "$baseUrl/${fileName}"
}