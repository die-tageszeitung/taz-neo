package de.taz.app.android.persistence.repository

import androidx.room.Transaction
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.api.models.ResourceInfoWithoutFiles
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.ResourceInfoFileEntryJoin

class ResourceInfoRepository(private val appDatabase: AppDatabase = AppDatabase.getInstance()) {

    private val fileEntryRepository = FileEntryRepository(appDatabase)

    @Transaction
    fun save(resourceInfo: ResourceInfo) {
        appDatabase.resourceInfoDao().insertOrReplace(
            ResourceInfoWithoutFiles(
                resourceInfo.resourceVersion,
                resourceInfo.resourceBaseUrl,
                resourceInfo.resourceZip
            )
        )
        // save file resourceList
        fileEntryRepository.save(
            resourceInfo.resourceList.map { FileEntry(it) }
        )
        // save relation to files
        appDatabase.resourceInfoFileEntryJoinDao().insertOrReplace(
            resourceInfo.resourceList.map {
                ResourceInfoFileEntryJoin(resourceInfo.resourceVersion, it.name)
            }
        )
    }

    fun getWithoutFilesOrThrow(): ResourceInfoWithoutFiles {
        getWithoutFiles()?.let {
            return it
        }
        throw NotFoundException()
    }

    fun getWithoutFiles(): ResourceInfoWithoutFiles? {
        return appDatabase.resourceInfoDao().get()
    }

    fun getOrThrow(): ResourceInfo {
        val resourceInfoWithoutFiles = appDatabase.resourceInfoDao().get()
        val resourceList = appDatabase.resourceInfoFileEntryJoinDao().getFileEntriesForResourceInfo(
            resourceInfoWithoutFiles.resourceVersion
        )
        try {
            return ResourceInfo(
                resourceInfoWithoutFiles.resourceVersion,
                resourceInfoWithoutFiles.resourceBaseUrl,
                resourceInfoWithoutFiles.resourceZip,
                resourceList.map { FileEntry(it.name, it.storageType, it.moTime, it.sha256, it.size) }
            )
        } catch (e: Exception) {
            throw NotFoundException()
        }
    }

    fun get(): ResourceInfo? {
        return try {
            getOrThrow()
        } catch (e: Exception) {
            null
        }
    }
}