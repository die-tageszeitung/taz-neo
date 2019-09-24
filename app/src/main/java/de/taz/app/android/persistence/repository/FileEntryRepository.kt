package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Transaction
import de.taz.app.android.api.models.Download
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.SingletonHolder

class FileEntryRepository private constructor(
    applicationContext: Context
) : RepositoryBase(applicationContext) {

    companion object : SingletonHolder<FileEntryRepository, Context>(::FileEntryRepository)
    private val downloadRepository = DownloadRepository.getInstance(applicationContext)
    private val fileHelper = FileHelper.getInstance(applicationContext)

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

    @Throws(NotFoundException::class)
    fun getOrThrow(fileEntryName: String): FileEntry {
        return get(fileEntryName) ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(fileEntryNames: List<String>): List<FileEntry> {
        return fileEntryNames.map { getOrThrow(it) }
    }

    fun delete(fileEntry: FileEntry) {
        // delete file and download if downloaded before
        downloadRepository.getWithoutFile(fileEntry.name)?.let { downloadBase ->
            val download = Download(downloadBase, fileEntry)
            fileHelper.deleteFileForDownload(download)
            downloadRepository.delete(fileEntry.name)
        }
        appDatabase.fileEntryDao().delete(fileEntry)
    }

}