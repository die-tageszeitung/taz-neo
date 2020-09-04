package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.util.SingletonHolder

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
                    fileEntry.copy(
                        downloadedStatus = fromDB.downloadedStatus
                    )
                )
            }
        } ?: appDatabase.fileEntryDao().insertOrReplace(fileEntry)
    }

    fun saveOrReplace(fileEntry: FileEntry) {
        appDatabase.fileEntryDao().insertOrReplace(fileEntry)
    }

    fun save(fileEntries: List<FileEntry>) {
        fileEntries.forEach { save(it) }
    }

    fun get(fileEntryName: String): FileEntry? {
        return appDatabase.fileEntryDao().getByName(fileEntryName)
    }

    fun getLiveData(fileEntryName: String): LiveData<FileEntry?> {
        return appDatabase.fileEntryDao().getLiveDataByName(fileEntryName)
    }

    fun get(fileEntryNames: List<String>): List<FileEntry> {
        return appDatabase.fileEntryDao().getByNames(fileEntryNames)
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(fileEntryName: String): FileEntry {
        return get(fileEntryName) ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(fileEntryNames: List<String>): List<FileEntry> {
        return fileEntryNames.map { getOrThrow(it) }
    }

    fun delete(fileEntryName: String) {
        get(fileEntryName)?.let { delete(it) }
    }

    fun delete(fileEntry: FileEntry) {
        appDatabase.downloadDao().apply {
            get(fileEntry.name)?.let {
                delete(it)
            }
        }
        appDatabase.fileEntryDao().delete(fileEntry)
    }

    fun delete(fileEntries: List<FileEntry>) {
        fileEntries.map { delete(it) }
    }

    fun isDownloadedLiveData(fileEntry: FileEntry) = isDownloadedLiveData(fileEntry.name)

    fun isDownloadedLiveData(fileName: String): LiveData<Boolean> {
        return appDatabase.fileEntryDao().isDownloadedLiveData(fileName)
    }

    fun setDownloadStatus(fileName: String, downloadStatus: DownloadStatus) {
        get(fileName)?.let {
            update(it.copy(downloadedStatus = downloadStatus))
        }
    }
}