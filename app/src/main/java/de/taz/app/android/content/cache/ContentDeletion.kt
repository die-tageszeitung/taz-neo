package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.models.Article
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.singletons.StorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cache operation deleting the files related to the collection [collection]
 *
 * @param applicationContext An android application context object
 * @param items The [FileEntryOperation] representing all files that should be deleted
 * @param collection The collection the content of which should be deleted
 * @param tag A tag on which this operation should be registered
 */
class ContentDeletion(
    applicationContext: Context,
    items: List<FileCacheItem>,
    private val collection: DownloadableCollection,
    tag: String,
) : CacheOperation<FileCacheItem, Unit>(
    applicationContext, items, CacheState.METADATA_PRESENT, tag
) {
    override val loadingState: CacheState = CacheState.DELETING_CONTENT

    private val storageService = StorageService.getInstance(applicationContext)

    companion object {
        /**
         * Prepare the deletion by collecting all [de.taz.app.android.api.models.FileEntry] belonging to
         * the [collection]. Filter out files that might be worth to retain.
         * @param applicationContext An android application context object
         * @param collection The collection the content of which should be deleted
         * @param tag A tag on which this operation should be registered
         */
        suspend fun prepare(
            applicationContext: Context,
            collection: DownloadableCollection,
            tag: String
        ): ContentDeletion = withContext(Dispatchers.IO) {
            val articleRepository = ArticleRepository.getInstance(applicationContext)

            val filesToDelete = if (collection is Article) {
                // If the collection is an article we need to make sure that it is the last
                // article referencing a file. If the reference count is 1 (or less) the
                // article that is about to get delete is the last one, so it's ok to delete
                collection.getAllFiles()
                    .filter {
                        articleRepository.getDownloadedArticleAuthorReferenceCount(it.name) < 2 &&
                        articleRepository.getDownloadedArticleImageReferenceCount(it.name) < 2
                    }
            } else {
                // If the collection is a section (or page or anything) check if an article is still referencing
                // it. (Most likely a bookmark)
                collection.getAllFiles()
                    .filter {
                        articleRepository.getDownloadedArticleAuthorReferenceCount(it.name) < 1 &&
                        articleRepository.getDownloadedArticleImageReferenceCount(it.name) < 1
                    }
            }

            val fileCacheItems = filesToDelete.map {
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
                applicationContext,
                fileCacheItems,
                collection,
                tag
            )
        }
    }

    override suspend fun doWork() = withContext(Dispatchers.IO) {
        notifyStart()
        // Reset the collection download date immediately. Even if the deletion has issues it's
        // better to assume the content deleted
        collection.setDownloadDate(null, applicationContext)
        try {
            for (item in cacheItems) {
                storageService.deleteFile(item.item.fileEntryOperation.fileEntry)
            }
        } catch (e: Exception) {
            notifyFailure(e)
        }
        notifySuccess(Unit)
    }
}