package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.util.SingletonHolder
import java.util.*

@Mockable
class FileEntryRepository private constructor(
    applicationContext: Context
) : RepositoryBase(applicationContext) {

    companion object : SingletonHolder<FileEntryRepository, Context>(::FileEntryRepository)

    suspend fun update(fileEntry: FileEntry) {
        appDatabase.fileEntryDao().update(fileEntry)
    }

    suspend fun save(fileEntry: FileEntry) {
        val fromDB = appDatabase.fileEntryDao().getByName(fileEntry.name)
        fromDB?.let {
            if (fromDB.moTime < fileEntry.moTime) {
                appDatabase.fileEntryDao().insertOrReplace(
                    fileEntry
                )
            }
        } ?: appDatabase.fileEntryDao().insertOrReplace(fileEntry)
    }

    suspend fun saveOrReplace(fileEntry: FileEntry): FileEntry {
        appDatabase.fileEntryDao().insertOrReplace(fileEntry)
        return fileEntry
    }

    suspend fun save(fileEntries: List<FileEntry>) {
        fileEntries.forEach { save(it) }
    }

    suspend fun get(fileEntryName: String): FileEntry? {
        return appDatabase.fileEntryDao().getByName(fileEntryName)
    }

    suspend fun getDownloaded(): List<FileEntry> {
        return appDatabase.fileEntryDao().getDownloaded()
    }

    suspend fun getDownloadedByStorageLocation(storageLocation: StorageLocation): List<FileEntry> {
        return appDatabase.fileEntryDao().getDownloadedByStorageLocation(storageLocation)
    }

    suspend fun getExceptStorageLocation(storageLocation: List<StorageLocation>): List<FileEntry> {
        return appDatabase.fileEntryDao().getExceptStorageLocation(storageLocation)
    }

    suspend fun getDownloadedExceptStorageLocation(storageLocation: StorageLocation): List<FileEntry> {
        return appDatabase.fileEntryDao().getDownloadedExceptStorageLocation(storageLocation)
    }

    suspend fun getLiveData(fileEntryName: String): LiveData<FileEntry?> {
        return appDatabase.fileEntryDao().getLiveDataByName(fileEntryName)
    }

    suspend fun getList(fileEntryNames: List<String>): List<FileEntry> {
        return appDatabase.fileEntryDao().getByNames(fileEntryNames)
    }

    suspend fun getOrThrow(fileEntryName: String): FileEntry {
        return get(fileEntryName) ?: throw NotFoundException()
    }

    suspend fun getOrThrow(fileEntryNames: List<String>): List<FileEntry> {
        return fileEntryNames.map { getOrThrow(it) }
    }

    suspend fun delete(fileEntryName: String) {
        get(fileEntryName)?.let { delete(it) }
    }

    suspend fun delete(fileEntry: FileEntry) {
        appDatabase.fileEntryDao().delete(fileEntry)
    }


    suspend fun deleteList(fileEntryNames: List<String>) {
        appDatabase.fileEntryDao().deleteList(fileEntryNames)
    }

    suspend fun resetDownloadDate(fileEntry: FileEntry) {
        update(fileEntry.copy(dateDownload = null, storageLocation = StorageLocation.NOT_STORED))
    }

    suspend fun setDownloadDate(fileEntry: FileEntry, date: Date?) {
        update(fileEntry.copy(dateDownload = date))
    }

    suspend fun getDownloadDate(fileEntry: FileEntryOperations): Date? {
        return appDatabase.fileEntryDao().getDownloadDate(fileEntry.name)
    }
}