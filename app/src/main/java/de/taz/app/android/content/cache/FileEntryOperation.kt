package de.taz.app.android.content.cache

import de.taz.app.android.api.models.FileEntry

/**
 * A structure containing of a [FileEntry], it's download origin and a path where to store it
 * @param fileEntry A [FileEntry] representing a file
 * @param destination An absolute path where that file is, or should be stored
 * @param origin A web url from where this file can be downloaded
 */
data class FileEntryOperation(
    val fileEntry: FileEntry,
    val destination: String?,
    val origin: String?
)