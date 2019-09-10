package de.taz.app.android.persistence.repository

import androidx.room.Transaction
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.AppDatabase

class FileEntryRepository(private val appDatabase: AppDatabase = AppDatabase.getInstance()) {


    @Transaction
    fun save(fileEntry: FileEntry) {
        val fromDB = appDatabase.fileEntryDao().getByName(fileEntry.name)
        fromDB?.let {
            if (fromDB.moTime > fileEntry.moTime)
                return@save
        }
        appDatabase.fileEntryDao().insertOrReplace(fileEntry)
    }

   @Transaction
    fun save(fileEntries: List<FileEntry>) {
        fileEntries.forEach { save(it) }
    }

    fun get(fileEntryName: String): FileEntry? {
        return appDatabase.fileEntryDao().getByName(fileEntryName)
    }

    fun getOrThrow(fileEntryName: String): FileEntry {
        return get(fileEntryName) ?: throw NotFoundException()
    }

    fun getOrThrow(fileEntryNames: List<String>): List<FileEntry> {
        try {
            return fileEntryNames.map { getOrThrow(it) }
        } catch (e: Exception) {
            throw NotFoundException()
        }
    }

    fun delete(fileEntry: FileEntry) {
        // TODO fileEntry.deleteFromDisk()
        appDatabase.fileEntryDao().delete(fileEntry)
    }

}