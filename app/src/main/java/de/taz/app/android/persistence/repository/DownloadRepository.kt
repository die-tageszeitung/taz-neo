package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.api.models.*
import de.taz.app.android.util.SingletonHolder
import java.util.*
import kotlin.Exception

class DownloadRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<DownloadRepository, Context>(::DownloadRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    @UiThread
    @Throws(NotFoundException::class)
    fun save(download: Download) {
        appDatabase.runInTransaction {
            appDatabase.fileEntryDao().getByName(download.file.name)?.let {
                val downloadStub = DownloadStub(download)
                appDatabase.downloadDao().insertOrReplace(downloadStub)
            } ?: throw NotFoundException()
        }
    }

    @UiThread
    fun saveIfNotExists(download: Download) {
        appDatabase.runInTransaction {
            appDatabase.fileEntryDao().getByName(download.file.name)?.let {
                val downloadStub = DownloadStub(download)
                try {
                    appDatabase.downloadDao().insertOrAbort(downloadStub)
                } catch (_: SQLiteConstraintException) {
                    // do nothing as already exists
                }
            } ?: throw NotFoundException()
        }
    }

    @UiThread
    fun update(download: Download) {
        appDatabase.downloadDao().update(DownloadStub(download))
    }

    @UiThread
    fun update(downloadStub: DownloadStub) {
        appDatabase.downloadDao().update(downloadStub)
    }

    @UiThread
    fun getWithoutFile(fileName: String): DownloadStub? {
        return appDatabase.downloadDao().get(fileName)
    }

    @UiThread
    fun getWithoutFile(fileNames: List<String>): List<DownloadStub?> {
        return appDatabase.downloadDao().get(fileNames)
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun getWithoutFileOrThrow(fileName: String): DownloadStub {
        return getWithoutFile(fileName) ?: throw NotFoundException()
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun getOrThrow(fileName: String): Download {
        try {
            val downloadStub = getWithoutFileOrThrow(fileName)
            val file = fileEntryRepository.getOrThrow(fileName)

            return Download(
                downloadStub,
                file
            )
        } catch (e: Exception) {
            throw NotFoundException()
        }
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun getOrThrow(fileNames: List<String>): List<Download> {
        return fileNames.map { getOrThrow(it) }
    }

    @UiThread
    fun get(fileName: String): Download? {
        return try {
            getOrThrow(fileName)
        } catch (e: Exception) {
            null
        }
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun setWorkerId(fileName: String, workerID: UUID) {
        appDatabase.runInTransaction {
            getWithoutFileOrThrow(fileName).let { downloadStub ->
                downloadStub.workerManagerId = workerID
                update(downloadStub)
            }
        }
    }

    @UiThread
    @Throws(NotFoundException::class)
    fun setStatus(download: Download, downloadStatus: DownloadStatus) {
        appDatabase.runInTransaction {
            getWithoutFileOrThrow(download.file.name).let {
                it.status = downloadStatus
                update(it)
            }
        }
    }

    @UiThread
    fun delete(fileName: String) {
        appDatabase.runInTransaction {
            try {
                appDatabase.downloadDao().delete(getWithoutFileOrThrow(fileName))
            } catch (e: Exception) {
                // do nothing already deleted
            }
        }
    }

    @UiThread
    fun isDownloaded(fileName: String): Boolean {
        return getWithoutFile(fileName)?.status == DownloadStatus.done
    }

    @UiThread
    fun isDownloaded(fileNames: List<String>): Boolean {
        val downloads = getWithoutFile(fileNames)
        return downloads.size == fileNames.size && downloads.firstOrNull { download ->
            download?.status != DownloadStatus.done
        } == null
    }

    @UiThread
    fun isDownloadedOrDownloading(fileNames: List<String>): Boolean {
        val downloads = getWithoutFile(fileNames)
        return downloads.size == fileNames.size && downloads.firstOrNull { download ->
            !arrayOf(DownloadStatus.done, DownloadStatus.started).contains(download?.status)
        } == null
    }

    @UiThread
    fun getLiveData(fileName: String): LiveData<Boolean> {
        return Transformations.map(appDatabase.downloadDao().getLiveData(fileName)) { input ->
            input?.status == DownloadStatus.done
        }
    }

    @UiThread
    fun isDownloadedLiveData(fileNames: List<String>): LiveData<Boolean> {
        val mediatorLiveData = MediatorLiveData<Boolean>()
        mediatorLiveData.postValue(false)

        var trueCountdown = fileNames.size
        fileNames.forEach { fileName ->
            val source = getLiveData(fileName)
            mediatorLiveData.addSource(source) { value ->
                if (value) {
                    trueCountdown--
                    mediatorLiveData.removeSource(source)
                }

                if (trueCountdown <= 0) {
                    mediatorLiveData.postValue(true)
                }
            }
        }

        return mediatorLiveData
    }
}