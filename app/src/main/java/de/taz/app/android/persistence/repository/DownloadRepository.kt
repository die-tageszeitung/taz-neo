package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.*
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import java.util.*
import kotlin.Exception

@Mockable
class DownloadRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<DownloadRepository, Context>(::DownloadRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val log by Log

    @Throws(NotFoundException::class)
    fun save(download: Download) {
        appDatabase.runInTransaction {
            appDatabase.fileEntryDao().getByName(download.file.name)?.let {
                val downloadStub = DownloadStub(download)
                appDatabase.downloadDao().insertOrReplace(downloadStub)
            } ?: throw NotFoundException()
        }
    }

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

    fun update(download: Download) {
        appDatabase.downloadDao().update(DownloadStub(download))
    }

    fun update(downloadStub: DownloadStub) {
        appDatabase.downloadDao().update(downloadStub)
    }

    fun getWithoutFile(fileName: String): DownloadStub? {
        return appDatabase.downloadDao().get(fileName)
    }

    fun getWithoutFileLiveData(fileName: String): LiveData<DownloadStub?> {
        return appDatabase.downloadDao().getLiveData(fileName)
    }

    fun getWithoutFile(fileNames: List<String>): List<DownloadStub?> {
        return appDatabase.downloadDao().get(fileNames)
    }

    fun getWithoutFileLiveData(fileNames: List<String>): List<LiveData<DownloadStub?>> {
        return fileNames.map { appDatabase.downloadDao().getLiveData(it) }
    }

    @Throws(NotFoundException::class)
    fun getWithoutFileOrThrow(fileName: String): DownloadStub {
        return getWithoutFile(fileName) ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(fileName: String): Download {
        val downloadStub = getWithoutFileOrThrow(fileName)
        val file = fileEntryRepository.getOrThrow(fileName)

        return Download(
            downloadStub,
            file
        )
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(fileNames: List<String>): List<Download> {
        return fileNames.map { getOrThrow(it) }
    }

    fun get(fileName: String): Download? {
        return try {
            getOrThrow(fileName)
        } catch (e: Exception) {
            null
        }
    }

    @Throws(NotFoundException::class)
    fun setWorkerId(fileName: String, workerID: UUID) {
        appDatabase.runInTransaction {
            getWithoutFileOrThrow(fileName).let { downloadStub ->
                downloadStub.workerManagerId = workerID
                update(downloadStub)
            }
        }
    }

    fun setStatus(download: Download, downloadStatus: DownloadStatus) {
        appDatabase.runInTransaction {
            try {
                update(getWithoutFileOrThrow(download.file.name).copy(status = downloadStatus))
            } catch (e: NotFoundException) {
                log.error("${e.message.toString()}: ${download.file.name}")
            }
        }
    }

    fun delete(fileName: String) {
        appDatabase.runInTransaction {
            try {
                appDatabase.downloadDao().delete(getWithoutFileOrThrow(fileName))
            } catch (e: Exception) {
                // do nothing already deleted
            }
        }
    }

    fun isDownloaded(fileName: String): Boolean {
        return getWithoutFile(fileName)?.status == DownloadStatus.done
    }

    fun isDownloaded(fileNames: List<String>): Boolean {
        val downloads = getWithoutFile(fileNames)
        return downloads.size == fileNames.size && downloads.firstOrNull { download ->
            download?.status != DownloadStatus.done
        } == null
    }

    fun isDownloadedOrDownloading(fileNames: List<String>): Boolean {
        val downloads = getWithoutFile(fileNames)
        return downloads.size == fileNames.size && downloads.firstOrNull { download ->
            !arrayOf(DownloadStatus.done, DownloadStatus.started).contains(download?.status)
        } == null
    }

    fun isDownloadedLiveData(fileNames: List<String>): LiveData<Boolean> {
        val mediatorLiveData = MediatorLiveData<Boolean>()
        mediatorLiveData.postValue(false)

        var trueCountdown = fileNames.size
        fileNames.forEach { fileName ->
            val source =
                Transformations.map(appDatabase.downloadDao().getLiveData(fileName)) { input ->
                    input?.status == DownloadStatus.done
                }
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

    fun isDownloadingOrDownloadedLiveData(fileNames: List<String>): LiveData<Boolean> {
        val downloadLiveDataList = getWithoutFileLiveData(fileNames)

        val mediatorLiveData = MediatorLiveData<Boolean>()
        downloadLiveDataList.forEach {
            mediatorLiveData.addSource(Transformations.distinctUntilChanged(it)) {
                mediatorLiveData.postValue(
                    downloadLiveDataList.fold(true) { acc: Boolean, liveData: LiveData<DownloadStub?> ->
                        acc && liveData.value != null
                    }
                )
            }
        }
        return mediatorLiveData
    }

}