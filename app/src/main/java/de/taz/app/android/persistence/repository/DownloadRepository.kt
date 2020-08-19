package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.DownloadOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.util.SingletonHolder
import java.util.*
import kotlin.Exception

@Mockable
class DownloadRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<DownloadRepository, Context>(::DownloadRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    @Throws(NotFoundException::class)
    fun save(download: Download) {
        appDatabase.fileEntryDao().getByName(download.file.name)?.let {
            val downloadStub = DownloadStub(download)
            appDatabase.downloadDao().insertOrReplace(downloadStub)
        } ?: throw NotFoundException()
    }

    /**
     * @return Boolean indicating whether the download has been saved or not
     */
    fun saveIfNotExists(download: Download): Boolean {
        appDatabase.fileEntryDao().getByName(download.file.name)?.let {
            val downloadStub = DownloadStub(download)
            return try {
                appDatabase.downloadDao().insertOrAbort(downloadStub)
                true
            } catch (_: SQLiteConstraintException) {
                // do nothing as already exists
                false
            }
        } ?: throw NotFoundException()
    }

    fun update(download: Download) {
        appDatabase.downloadDao().update(DownloadStub(download))
    }

    fun update(downloadStub: DownloadStub) {
        appDatabase.downloadDao().update(downloadStub)
    }

    fun getStub(fileName: String): DownloadStub? {
        return appDatabase.downloadDao().get(fileName)
    }

    fun getStub(fileNames: List<String>): List<DownloadStub?> {
        return appDatabase.downloadDao().get(fileNames)
    }

    @Throws(NotFoundException::class)
    fun getWithoutFileOrThrow(fileName: String): DownloadStub {
        return getStub(fileName) ?: throw NotFoundException()
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
        getWithoutFileOrThrow(fileName).let { downloadStub ->
            downloadStub.workerManagerId = workerID
            update(downloadStub)
        }
    }

    fun saveLastSha256(download: Download, sha256: String) =
        saveLastSha256(DownloadStub(download), sha256)

    fun saveLastSha256(downloadStub: DownloadStub, sha256: String) {
        try {
            update(downloadStub.copy(lastSha256 = sha256))
        } catch (e: NotFoundException) {
            log.error("${e.message.toString()}: ${downloadStub.fileName}")
        }
    }

    fun setStatus(fileName: String, downloadStatus: DownloadStatus) {
        try {
            update(getWithoutFileOrThrow(fileName).copy(status = downloadStatus))
        } catch (e: NotFoundException) {
            log.error("${e.message.toString()}: $fileName")
        }
    }

    fun setStatus(download: DownloadOperations, downloadStatus: DownloadStatus) {
        setStatus(download.fileName, downloadStatus)
    }

    fun delete(fileName: String) {
        try {
            appDatabase.downloadDao().delete(getWithoutFileOrThrow(fileName))
        } catch (e: NotFoundException) {
            // do nothing already deleted
        }
    }

    fun isDownloaded(fileName: String): Boolean {
        return getStub(fileName)?.status == DownloadStatus.done
    }

    fun isDownloaded(fileNames: List<String>): Boolean {
        val downloads = getStub(fileNames)
        return downloads.size == fileNames.size && downloads.firstOrNull { download ->
            !arrayOf(DownloadStatus.done, DownloadStatus.takeOld).contains(
                download?.status
            )
        } == null
    }

    fun isDownloadedLiveData(fileName: String): LiveData<Boolean> {
        return isDownloadedLiveData(listOf(fileName))
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

}