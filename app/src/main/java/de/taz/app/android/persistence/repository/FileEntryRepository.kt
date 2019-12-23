package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.annotation.UiThread
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.util.SingletonHolder

class FileEntryRepository private constructor(
    applicationContext: Context
) : RepositoryBase(applicationContext) {

    companion object : SingletonHolder<FileEntryRepository, Context>(::FileEntryRepository)

    @UiThread
    fun save(fileEntry: FileEntry) {
        appDatabase.runInTransaction {
            val fromDB = appDatabase.fileEntryDao().getByName(fileEntry.name)
            fromDB?.let {
                if (fromDB.moTime < fileEntry.moTime)
                    appDatabase.fileEntryDao().insertOrReplace(fileEntry)
            } ?: appDatabase.fileEntryDao().insertOrReplace(fileEntry)
        }
    }

    @UiThread
    fun save(fileEntries: List<FileEntry>) {
        appDatabase.runInTransaction {
            fileEntries.forEach { save(it) }
        }
    }

    @UiThread
    fun get(fileEntryName: String): FileEntry? {
        return appDatabase.fileEntryDao().getByName(fileEntryName)
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun getOrThrow(fileEntryName: String): FileEntry {
        return get(fileEntryName) ?: throw NotFoundException()
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun getOrThrow(fileEntryNames: List<String>): List<FileEntry> {
        return fileEntryNames.map { getOrThrow(it) }
    }

    @UiThread
    fun delete(fileEntry: FileEntry) {
        appDatabase.runInTransaction {
            appDatabase.downloadDao().apply {
                get(fileEntry.name)?.let {
                    delete(it)
                }
            }
            appDatabase.fileEntryDao().delete(fileEntry)
        }
    }

    @UiThread
    fun delete(fileEntries: List<FileEntry>) {
        fileEntries.map { delete(it) }
    }

}