package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.util.SingletonHolder
import java.util.*

@Mockable
class FileEntryRepository private constructor(
    applicationContext: Context
) : RepositoryBase(applicationContext) {

    companion object : SingletonHolder<FileEntryRepository, Context>(::FileEntryRepository)

    fun update(fileEntry: FileEntry) {
        appDatabase.fileEntryDao().update(fileEntry)
    }

    fun save(fileEntry: FileEntry) {
        val fromDB = appDatabase.fileEntryDao().getByName(fileEntry.name)
        fromDB?.let {
            if (fromDB.moTime < fileEntry.moTime) {
                appDatabase.fileEntryDao().insertOrReplace(
                    fileEntry
                )
            }
        } ?: appDatabase.fileEntryDao().insertOrReplace(fileEntry)
    }

    fun saveOrReplace(fileEntry: FileEntry): FileEntry {
        appDatabase.fileEntryDao().insertOrReplace(fileEntry)
        return fileEntry
    }

    fun save(fileEntries: List<FileEntry>) {
        fileEntries.forEach { save(it) }
    }

    fun get(fileEntryName: String): FileEntry? {
        return appDatabase.fileEntryDao().getByName(fileEntryName)
    }

    fun getDownloaded(): List<FileEntry> {
        return appDatabase.fileEntryDao().getDownloaded()
    }

    fun getByStorageLocation(storageLocation: StorageLocation): List<FileEntry> {
        return appDatabase.fileEntryDao().getByStorageLocation(storageLocation)
    }

    fun getDownloadedByStorageLocation(storageLocation: StorageLocation): List<FileEntry> {
        return appDatabase.fileEntryDao().getByStorageLocation(storageLocation)
    }

    fun getDownloadedExceptStorageLocation(storageLocation: StorageLocation): List<FileEntry> {
        return appDatabase.fileEntryDao().getDownloadedExceptStorageLocation(storageLocation)
    }

    fun getLiveData(fileEntryName: String): LiveData<FileEntry?> {
        return appDatabase.fileEntryDao().getLiveDataByName(fileEntryName)
    }

    fun getList(fileEntryNames: List<String>): List<FileEntry> {
        return appDatabase.fileEntryDao().getByNames(fileEntryNames)
    }

    fun getOrThrow(fileEntryName: String): FileEntry {
        return get(fileEntryName) ?: throw NotFoundException()
    }

    fun getOrThrow(fileEntryNames: List<String>): List<FileEntry> {
        return fileEntryNames.map { getOrThrow(it) }
    }

    fun delete(fileEntryName: String) {
        get(fileEntryName)?.let { delete(it) }
    }

    fun delete(fileEntry: FileEntry) {
        appDatabase.fileEntryDao().delete(fileEntry)
    }


    fun deleteList(fileEntryNames: List<String>) {
        appDatabase.fileEntryDao().deleteList(fileEntryNames)
    }

    fun resetDownloadDate(fileEntry: FileEntry) {
        update(fileEntry.copy(dateDownload = null, storageLocation = StorageLocation.NOT_STORED))
    }

    fun setDownloadDate(fileEntry: FileEntry, date: Date?) {
        update(fileEntry.copy(dateDownload = date))
    }

    fun getDownloadDate(fileEntry: FileEntryOperations): Date? {
        return appDatabase.fileEntryDao().getDownloadDate(fileEntry.name)
    }
}