package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.download.FileDownloader
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StoragePathService
import de.taz.app.android.singletons.StorageService
import kotlinx.coroutines.*
import java.util.*

/**
 * Cache operation downloading the files related to the collection [collection]
 *
 * @param applicationContext An android application context object
 * @param items The [FileCacheItem]s representing all files that should be downloaded
 * @param tag A tag on which this operation should be registered
 * @param collection The collection the content of which should be downloaded
 * @param priority The priority that will be passed to the files that are to be downloaded
 */
class ContentDownload(
    applicationContext: Context,
    items: List<FileCacheItem>,
    tag: String,
    private val collection: DownloadableCollection?,
    priority: DownloadPriority
) : CacheOperation<FileCacheItem, Unit>(
    applicationContext, items, CacheState.PRESENT, tag, priority
) {
    override val loadingState: CacheState = CacheState.LOADING_CONTENT
    private val fileDownloader = FileDownloader.getInstance(applicationContext)

    companion object {
        /**
         * Preparing a [ContentDownload] object by determining the list of files
         * that need to be downloaded and its origin url and destination path.
         *
         * @param applicationContext An android application context object
         * @param collection The collection the content of which should be downloaded
         * @param priority The priority that will be passed to the files that are to be downloaded
         */
        suspend fun prepare(
            applicationContext: Context,
            collection: DownloadableCollection,
            priority: DownloadPriority
        ): ContentDownload {
            val storagePathService = StoragePathService.getInstance(applicationContext)
            val tag = collection.getDownloadTag()
            val storageDataStore = StorageDataStore.getInstance(applicationContext)
            val storageService = StorageService.getInstance(applicationContext)
            val storageLocation = storageDataStore.storageLocation.get()
            val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
            val prioritizedDownloads = collection.getAllFiles(applicationContext)
                .map {
                    // Set the storage type to the currently selected storage
                    val fileEntry = fileEntryRepository.saveOrReplace(
                        it.copy(storageLocation = storageLocation)
                    )
                    // Determine the url from where this file entry should be downloaded as well as
                    // the path where this file should be saved
                    val absolutePath = storageService.getAbsolutePath(fileEntry)!!
                    val baseUrl = storagePathService.determineBaseUrl(
                        fileEntry,
                        collection
                    )
                    FileCacheItem(
                        fileEntry.name,
                        { priority },
                        FileEntryOperation(
                            fileEntry,
                            absolutePath,
                            "$baseUrl/${fileEntry.name}",
                        )
                    )
                }
            return ContentDownload(
                applicationContext,
                prioritizedDownloads,
                tag,
                collection,
                priority
            )
        }

        /**
         * Preparing a [ContentDownload] object for a single file by determining the origin and destination uri
         *
         * @param context An android context object
         * @param item The [FileEntry] that should be downloaded
         * @param priority The priority that will be passed to the files that are to be downloaded
         */
        suspend fun prepare(
            context: Context,
            item: FileEntry,
            baseUrl: String,
            priority: DownloadPriority
        ): ContentDownload {
            val tag = item.name
            val storageDataStore = StorageDataStore.getInstance(context)
            val storageService = StorageService.getInstance(context)
            val storageLocation = storageDataStore.storageLocation.get()
            val fileEntryRepository = FileEntryRepository.getInstance(context)

            // Set the storage type to the currently selected storage
            val updatedFileEntry = fileEntryRepository.saveOrReplace(
                item.copy(storageLocation = storageLocation)
            )
            val absolutePath = storageService.getAbsolutePath(updatedFileEntry)!!

            val cacheItem = FileCacheItem(
                updatedFileEntry.name,
                { priority },
                FileEntryOperation(
                    updatedFileEntry,
                    absolutePath,
                    "$baseUrl/${updatedFileEntry.name}",
                )
            )

            return ContentDownload(
                context,
                listOf(cacheItem),
                tag,
                null,
                priority
            )
        }
    }

    override suspend fun doWork() = withContext(Dispatchers.Default) {
        // Enqueue all downloads asynchronously
        launch { fileDownloader.enqueueDownload(this@ContentDownload) }

        waitOnCompletion()
        // download is done
        collection?.setDownloadDate(Date(), applicationContext)

        Unit
    }
}