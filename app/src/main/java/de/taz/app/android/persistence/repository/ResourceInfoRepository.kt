package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import de.taz.app.android.R
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.ImageResolution
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.api.models.ImageType
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.api.models.ResourceInfoStub
import de.taz.app.android.persistence.join.ResourceInfoFileEntryJoin
import de.taz.app.android.util.SingletonHolder
import java.util.Date


class ResourceInfoRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object : SingletonHolder<ResourceInfoRepository, Context>(::ResourceInfoRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val imageRepository = ImageRepository.getInstance(applicationContext)
    private val defaultNavButtonDrawerFileName =
        applicationContext.getString(R.string.DEFAULT_NAV_DRAWER_FILE_NAME)

    suspend fun update(resourceInfoStub: ResourceInfoStub) {
        appDatabase.resourceInfoDao().update(resourceInfoStub)
    }

    /**
     * Save the full downloaded [ResourceInfo] metadata to the database
     * and replace any existing [ResourceInfo] with the same key.
     *
     * This will recursively save all the related models.
     * As there are many-to-many relations, replacing an existing [ResourceInfo] might result in some
     * orphaned children that have to be cleanup up by some scrubber process.
     */
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

            // Ensure, that the defaultNavButtonDrawerFileName Image entry exists
            val imageFileEntry =
                resourceInfo.resourceList.findLast { it.name == defaultNavButtonDrawerFileName }
            if (imageFileEntry != null){
                val imageStub = ImageStub(
                    defaultNavButtonDrawerFileName,
                    ImageType.button,
                    alpha = 1f,
                    ImageResolution.normal
                )
                imageRepository.saveInternal(Image(imageFileEntry, imageStub))
            }

            // save relation to files
            appDatabase.resourceInfoFileEntryJoinDao().apply {
                deleteRelationToResourceInfo(resourceInfo.resourceVersion)
                insertOrReplace(resourceInfo.resourceList.mapIndexed { index, fileEntry ->
                    ResourceInfoFileEntryJoin(
                        resourceInfo.resourceVersion, fileEntry.name, index
                    )
                })
            }
        }
        return requireNotNull(getNewest()) { "Could not get $resourceInfo after it was saved" }
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

    private suspend fun resourceInfoStubToResourceInfo(resourceInfoStub: ResourceInfoStub): ResourceInfo {
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

}