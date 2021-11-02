package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.FileEntry

/**
 * Interface every model has to implement which can be downloaded with [de.taz.app.android.content.ContentService]
 */
interface DownloadableCollection: DownloadableStub {
    fun getAllFiles(): List<FileEntry>
    fun getAllFileNames(): List<String>
}