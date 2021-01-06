package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.download.DownloadService
import java.util.*

/**
 * Interface every model has to implement which can be downloaded with [DownloadService]
 */
interface DownloadableCollection: DownloadableStub {
    fun getAllFiles(): List<FileEntry>
    fun getAllFileNames(): List<String>
}