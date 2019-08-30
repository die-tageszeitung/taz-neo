package de.taz.app.android.persistence.repository

import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.api.models.ResourceInfoWithoutFiles
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.ResourceInfoFileEntryJoin

object ResourceInfoRepository {

    private val appDatabase = AppDatabase.getInstance()

    fun save(resourceInfo: ResourceInfo) {
        appDatabase.resourceInfoDao().insertOrReplace(
            ResourceInfoWithoutFiles(
                resourceInfo.resourceVersion,
                resourceInfo.resourceBaseUrl,
                resourceInfo.resourceZip
            )
        )
        // save file resourceList
        appDatabase.fileEntryDao().insertOrReplace(
            resourceInfo.resourceList.map { FileEntry(it) }
        )
        // save relation to files
        appDatabase.resourceInfoFileEntryJoinDao().insertOrReplace(
            resourceInfo.resourceList.map {
                ResourceInfoFileEntryJoin(resourceInfo.resourceVersion, it.name)
            }
        )
    }

    fun getWithoutFiles(): ResourceInfoWithoutFiles {
        return appDatabase.resourceInfoDao().get()
    }

    fun get(): ResourceInfo {
        val resourceInfoWithoutFiles = appDatabase.resourceInfoDao().get()
        val resourceList = appDatabase.resourceInfoFileEntryJoinDao().getFileEntriesForResourceInfo(
            resourceInfoWithoutFiles.resourceVersion
        )
        return  ResourceInfo(
            resourceInfoWithoutFiles.resourceVersion,
            resourceInfoWithoutFiles.resourceBaseUrl,
            resourceInfoWithoutFiles.resourceZip,
            resourceList.map { FileEntry(it.name, it.storageType, it.moTime, it.sha256, it.size) }
        )
    }
}