package de.taz.app.android.api.interfaces

import android.content.Context
import de.taz.app.android.api.models.StorageType
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.Storable

import java.util.*

interface FileEntryOperations: Storable {
    override val name: String
    override val storageType: StorageType
    val moTime: Long
    val sha256: String
    val size: Long
    @Deprecated("folder field deprecated, file path now stored in path")
    val folder: String
    val dateDownload: Date?
    val path: String
    val storageLocation: StorageLocation

    suspend fun getDownloadDate(applicationContext: Context): Date? {
        return FileEntryRepository.getInstance(applicationContext).getDownloadDate(this)
    }

    suspend fun setDownloadDate(date: Date?, applicationContext: Context) {
        val file = when (this) {
            is Image -> FileEntry(this)
            is FileEntry -> this
            else -> this as FileEntry
        }
        return FileEntryRepository.getInstance(applicationContext).setDownloadDate(file, date)
    }
}

enum class StorageLocation {
    NOT_STORED,
    EXTERNAL,
    INTERNAL;
}