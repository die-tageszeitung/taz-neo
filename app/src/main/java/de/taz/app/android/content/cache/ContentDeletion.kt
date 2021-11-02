package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cache operation deleting the files related to the collection [collection]
 *
 * @param context An android context object
 * @param items The [FileEntryOperation] representing all files that should be deleted
 * @param collection The collection the content of which should be deleted
 * @param tag A tag on which this operation should be registered
 */
class ContentDeletion(
    context: Context,
    items: List<FileCacheItem>,
    private val collection: DownloadableCollection,
    tag: String,
) : CacheOperation<FileCacheItem, Unit>(
    context, items, CacheState.METADATA_PRESENT, tag
) {
    val storageService = StorageService.getInstance(context)
    override val loadingState: CacheState = CacheState.DELETING_CONTENT

    companion object {
        /**
         * Prepare the deletion by collecting all [de.taz.app.android.api.models.FileEntry] belonging to
         * the [collection]. Filter out files that might be worth to retain.
         * @param context An android context object
         * @param collection The collection the content of which should be deleted
         * @param tag A tag on which this operation should be registered
         */
        suspend fun prepare(
            context: Context,
            collection: DownloadableCollection,
            tag: String
        ): ContentDeletion = withContext(Dispatchers.IO) {
            val fileEntryRepository = FileEntryRepository.getInstance(context)
            val filesToDelete = collection.getAllFiles()
                .map { it.name }
                .let {
                    // Authors may belong to multiple articles. Deleting those would
                    // affect other article's resources as well 💩
                    fileEntryRepository.filterFilesThatBelongToAnAuthor(
                        it
                    )
                }
                .map {
                    FileCacheItem(
                        it.name,
                        { DownloadPriority.Normal },
                        FileEntryOperation(
                            it,
                            null,  // We don't need the destination for deletion
                            null  // We don't need the origin for deletion
                        )
                    )
                }

            return@withContext ContentDeletion(
                context,
                filesToDelete,
                collection,
                tag
            )
        }
    }

    override suspend fun doWork() = withContext(Dispatchers.IO) {
        notifyStart()
        // Reset the collection download date immediately. Even if the deletion has issues it's
        // better to assume the content deleted
        collection.setDownloadDate(null, context)
        try {
            for (item in cacheItems) {
                storageService.deleteFile(item.item.fileEntryOperation.fileEntry)
            }
        } catch (e: Exception) {
            notifyFailiure(e)
        }
        notifySuccess(Unit)
    }
}