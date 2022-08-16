package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.room.withTransaction
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.ResourceInfoFileEntryJoin
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.*

@Mockable
class ResourceInfoRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object : SingletonHolder<ResourceInfoRepository, Context>(::ResourceInfoRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    suspend fun update(resourceInfoStub: ResourceInfoStub) {
        appDatabase.resourceInfoDao().update(resourceInfoStub)
    }

    suspend fun save(resourceInfo: ResourceInfo): ResourceInfo {
        appDatabase.withTransaction {
            val currentResourceInfo = getNewest()
            // If the latest resource info is marked as downloaded and the new version
            // is not different copy over the download date
            val dateDownload = if (
                currentResourceInfo != null &&
                currentResourceInfo.resourceVersion == resourceInfo.resourceVersion
            ) currentResourceInfo.dateDownload else null
            appDatabase.resourceInfoDao().insertOrReplace(
                ResourceInfoStub(
                    resourceInfo.resourceVersion,
                    resourceInfo.resourceBaseUrl,
                    resourceInfo.resourceZip,
                    dateDownload ?: resourceInfo.dateDownload,
                )
            )
            // save file resourceList
            fileEntryRepository.save(
                resourceInfo.resourceList
            )
            // save relation to files
            appDatabase.resourceInfoFileEntryJoinDao().insertOrReplace(
                resourceInfo.resourceList.mapIndexed { index, fileEntry ->
                    ResourceInfoFileEntryJoin(resourceInfo.resourceVersion, fileEntry.name, index)
                }
            )
        }
        return getNewest()!!
    }

    suspend fun getWithoutFiles(): ResourceInfoStub? {
        return appDatabase.resourceInfoDao().getNewest()
    }

    suspend fun getStub(): ResourceInfoStub? {
        return appDatabase.resourceInfoDao().getNewest()
    }

    suspend fun getNewest(): ResourceInfo? {
        return appDatabase.resourceInfoDao().getNewest()?.let { resourceInfoStubToResourceInfo(it) }
    }

    suspend fun getLiveData(): LiveData<ResourceInfo?> {
        return Transformations.map(appDatabase.resourceInfoDao().getLiveData()) {
            runBlocking {
                it?.let { resourceInfoStubToResourceInfo(it) }
            }
        }
    }

    suspend fun resourceInfoStubToResourceInfo(resourceInfoStub: ResourceInfoStub): ResourceInfo {
        val resourceList = appDatabase.resourceInfoFileEntryJoinDao().getFileEntriesForResourceInfo(
            resourceInfoStub.resourceVersion
        )
        return ResourceInfo(
            resourceInfoStub.resourceVersion,
            resourceInfoStub.resourceBaseUrl,
            resourceInfoStub.resourceZip,
            resourceList,
            resourceInfoStub.dateDownload
        )
    }

    suspend fun delete(resourceInfo: ResourceInfo) {
        appDatabase.resourceInfoFileEntryJoinDao().delete(
            resourceInfo.resourceList.mapIndexed { index, fileEntry ->
                ResourceInfoFileEntryJoin(resourceInfo.resourceVersion, fileEntry.name, index)
            }
        )
        try {
            appDatabase.fileEntryDao().delete(resourceInfo.resourceList)
        } catch (e: SQLiteConstraintException) {
            log.warn("Error occurred: $e")
        }

        appDatabase.resourceInfoDao().delete(ResourceInfoStub(resourceInfo))
    }

    suspend fun getDownloadStatus(resourceInfo: ResourceInfo): Date? {
        return appDatabase.resourceInfoDao().getDownloadStatus(resourceInfo.resourceVersion)
    }


    suspend fun setDownloadStatus(resourceInfo: ResourceInfo, date: Date?) {
        return update(ResourceInfoStub(resourceInfo).copy(dateDownload = date))
    }


    suspend fun isDownloadedLiveData(resourceVersion: Int): LiveData<Boolean> {
        return appDatabase.resourceInfoDao().isDownloadedLiveData(resourceVersion)
    }
}