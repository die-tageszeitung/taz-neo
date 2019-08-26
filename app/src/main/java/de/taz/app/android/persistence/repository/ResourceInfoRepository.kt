package de.taz.app.android.persistence.repository

import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.entities.ResourceInfoEntity
import de.taz.app.android.persistence.entities.ResourceInfoFileEntryJoin

object ResourceInfoRepository {

    private val appDatabase = AppDatabase.getInstance()

    fun save(resourceInfo: ResourceInfo) {
        appDatabase.resourceInfoDao().insertOrReplace(
            ResourceInfoEntity(
                resourceInfo.resourceVersion,
                resourceInfo.resourceBaseUrl,
                resourceInfo.resourceZip)
        )
        appDatabase.fileEntryDao().insertOrReplace(
            resourceInfo.resourceList.map { FileEntry(it) }
        )
        appDatabase.resourceInfoFileEntryJoinDao().insertOrReplace(
            resourceInfo.resourceList.map {
                ResourceInfoFileEntryJoin(resourceInfo.resourceVersion, it.name)
            }
        )
    }

    fun get(): ResourceInfo {
        val resourceInfoEntity = appDatabase.resourceInfoDao().get()
        val resourceList = appDatabase.resourceInfoFileEntryJoinDao().getFileEntriesForResourceInfo(
            resourceInfoEntity.resourceVersion
        )
        return  ResourceInfo(
            resourceInfoEntity.resourceVersion,
            resourceInfoEntity.resourceBaseUrl,
            resourceInfoEntity.resourceZip,
            resourceList.map { FileEntry(it.name, it.storageType, it.moTime, it.sha256, it.size) }
        )
    }
}