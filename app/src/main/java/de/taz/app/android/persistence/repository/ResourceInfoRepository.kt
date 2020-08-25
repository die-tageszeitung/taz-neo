package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.api.models.ResourceInfoStub
import de.taz.app.android.persistence.join.ResourceInfoFileEntryJoin
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Mockable
class ResourceInfoRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object : SingletonHolder<ResourceInfoRepository, Context>(::ResourceInfoRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    fun update(resourceInfoStub: ResourceInfoStub) {
        appDatabase.resourceInfoDao().update(resourceInfoStub)
    }

    fun save(resourceInfo: ResourceInfo) {
        appDatabase.runInTransaction<Void> {
            appDatabase.resourceInfoDao().insertOrReplace(
                ResourceInfoStub(
                    resourceInfo.resourceVersion,
                    resourceInfo.resourceBaseUrl,
                    resourceInfo.resourceZip,
                    resourceInfo.downloadedStatus
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
            null
        }
    }

    fun getWithoutFiles(): ResourceInfoStub? {
        return appDatabase.resourceInfoDao().get()
    }

    fun getStub(): ResourceInfoStub? {
        return appDatabase.resourceInfoDao().get()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(): ResourceInfo {
        return resourceInfoStubToResourceInfo(appDatabase.resourceInfoDao().get())
    }

    fun getLiveData(): LiveData<ResourceInfo?> {
        return Transformations.map(appDatabase.resourceInfoDao().getLiveData()) {
            runBlocking(Dispatchers.IO) {
                it?.let { resourceInfoStubToResourceInfo(it) }
            }
        }
    }

    fun resourceInfoStubToResourceInfo(resourceInfoStub: ResourceInfoStub): ResourceInfo {
        val resourceList = appDatabase.resourceInfoFileEntryJoinDao().getFileEntriesForResourceInfo(
            resourceInfoStub.resourceVersion
        )
        return ResourceInfo(
            resourceInfoStub.resourceVersion,
            resourceInfoStub.resourceBaseUrl,
            resourceInfoStub.resourceZip,
            resourceList.map {
                FileEntry(
                    it.name,
                    it.storageType,
                    it.moTime,
                    it.sha256,
                    it.size,
                    RESOURCE_FOLDER,
                    it.downloadedStatus
                )
            },
            resourceInfoStub.downloadedStatus
        )
    }


    fun get(): ResourceInfo? {
        return try {
            getOrThrow()
        } catch (e: Exception) {
            null
        }
    }

    fun delete(resourceInfo: ResourceInfo) {
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

    fun deleteAllButNewest() {
        appDatabase.resourceInfoDao().getAllButNewest().forEach { resourceInfo ->
            delete(resourceInfoStubToResourceInfo(resourceInfo))
        }
    }

    fun isDownloadedLiveData(resourceVersion: Int): LiveData<Boolean> {
        return appDatabase.resourceInfoDao().isDownloadedLiveData(resourceVersion)
    }
}