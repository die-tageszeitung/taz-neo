package de.taz.app.android.persistence.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Transaction
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.AppDatabase
import java.util.*
import kotlin.Exception

class DownloadRepository(private val appDatabase: AppDatabase = AppDatabase.getInstance()) {

    private val fileEntryRepository = FileEntryRepository(appDatabase)

    @Transaction
    @Throws(NotFoundException::class)
    fun save(download: Download) {
        appDatabase.fileEntryDao().getByName(download.file.name)?.let {
            val downloadWithoutFile = DownloadWithoutFile(download)
            appDatabase.downloadDao().insertOrReplace(downloadWithoutFile)
        } ?: throw NotFoundException()
    }

    @Transaction
    fun saveIfNotExists(download: Download) {
        appDatabase.fileEntryDao().getByName(download.file.name)?.let {
            val downloadWithoutFile = DownloadWithoutFile(download)
            try {
                appDatabase.downloadDao().insertOrAbort(downloadWithoutFile)
            } catch (sqle: SQLiteConstraintException) {
                // do nothing as already exists
            }
        } ?: throw NotFoundException()
    }

    @Transaction
    fun update(download: Download) {
        appDatabase.downloadDao().update(DownloadWithoutFile(download))
    }

    @Transaction
    fun update(downloadWithoutFile: DownloadWithoutFile) {
        appDatabase.downloadDao().update(downloadWithoutFile)
    }

    fun getWithoutFile(fileName: String): DownloadWithoutFile? {
        return appDatabase.downloadDao().get(fileName)
    }

    @Throws(NotFoundException::class)
    fun getWithoutFileOrThrow(fileName: String): DownloadWithoutFile {
        return getWithoutFile(fileName) ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(fileName: String): Download {
        try {
            val downloadWithoutFile = getWithoutFileOrThrow(fileName)
            val file = fileEntryRepository.getOrThrow(fileName)

            return Download(
                downloadWithoutFile,
                file
            )
        } catch (e: Exception) {
            throw NotFoundException()
        }
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(fileNames: List<String>) : List<Download> {
        return fileNames.map { getOrThrow(it) }
    }

    fun get(fileName: String): Download? {
        return try {
            getOrThrow(fileName)
        } catch (e: Exception) {
            null
        }
    }

    @Transaction
    @Throws(NotFoundException::class)
    fun setWorkerId(fileName: String, workerID: UUID) {
        getWithoutFileOrThrow(fileName).let { downloadWithoutFile ->
            downloadWithoutFile.workerManagerId = workerID
            update(downloadWithoutFile)
        }
    }

    @Transaction
    @Throws(NotFoundException::class)
    fun setStatus(download: Download, downloadStatus: DownloadStatus) {
        getWithoutFileOrThrow(download.file.name).let {
            it.status = downloadStatus
            update(it)
        }
    }

    @Transaction
    fun delete(fileName: String) {
        try {
            appDatabase.downloadDao().delete(getWithoutFileOrThrow(fileName))
        } catch (e: Exception) {
            // do nothing already deleted
        }
    }

}