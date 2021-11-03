package de.taz.app.android.content

import android.content.Context
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.DownloadableStub
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.api.models.*
import de.taz.app.android.content.cache.*
import de.taz.app.android.download.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * The [ContentService] provides easy-to-use functions to download content (cache) for
 * any [ObservableDownload]. It should provide a simple and robust api for both
 * managing the content cache and listen to updates to that cache.
 *
 * @param context The Android [Context]
 */
@Mockable
class ContentService(
    private val context: Context
) {

    companion object : SingletonHolder<ContentService, Context>(::ContentService)

    private val issueRepository = IssueRepository.getInstance(context)
    private val cacheStatusFlow = CacheOperation.cacheStatusFlow
    private val activeCacheOperations = CacheOperation.activeCacheOperations

    /**
     * As [ObservableDownload]s will trigger multiple (sub) operations, concerning the
     * very same, we need to differentiate the "parent" [CacheOperation], that will live through the whole
     * lifecycle of all sub [CacheOperation]s. Therefore we need to prefix the parent one to avoid conflicts.
     * (Only one operation can be active per tag)
     */
    private fun determineParentTag(download: ObservableDownload): String {
        return "parent/${download.getDownloadTag()}"
    }

    /**
     * Get a [Flow] emitting [CacheStateUpdate]s concerning [download]
     *
     * @param download The download to listen updates on
     * @return A Flow object emitting [CacheStateUpdate]s
     */
    fun getCacheStatusFlow(download: ObservableDownload): Flow<CacheStateUpdate> {
        return getCacheStatusFlow(listOf(download))
    }

    /**
     * Get a [Flow] emitting updates concerning any [ObservableDownload] in [downloads]
     * It will immediately emit an update with the current state to inform the new subscriber
     * about the current cache state of the [ObservableDownload].
     * If in that moment no operation for [downloads] is ongoing the [CacheStateUpdate] may have
     * null for [CacheStateUpdate.operation]
     *
     * @param downloads The downloads to listen updates on
     * @return A Flow object emitting [CacheStateUpdate]s
     */
    fun getCacheStatusFlow(downloads: List<ObservableDownload>): Flow<CacheStateUpdate> {
        val parentTags = downloads.map { determineParentTag(it) }
        val tags = downloads.map { it.getDownloadTag() }
        return cacheStatusFlow
            .filter {
                // Receive updates to both the parent operation and any discrete operation
                parentTags.contains(it.first) || tags.contains(it.first)
            }
            .map { it.second }
            .also {
                downloads.forEach { download ->
                    val tag = determineParentTag(download)
                    // Immediately after subscribing check if there is a ongoing operation and push state if available
                    // if no operation is ongoing just determine the current state
                    CoroutineScope(Dispatchers.IO).launch {
                        val activeOperation = activeCacheOperations[tag]
                        if (activeOperation != null) {
                            cacheStatusFlow.emit(tag to activeOperation.state)
                        } else {
                            cacheStatusFlow.emit(tag to getCacheState(download))
                        }
                    }
                }
            }
    }

    /**
     * Determine the [CacheState] by looking at [DownloadableStub.isDownloaded]
     * and create a [CacheStateUpdate] from that information.
     *
     * @param observableDownload The [ObservableDownload] of which the state should be determined
     * @return A [CacheStateUpdate] indicating the current state of [observableDownload]
     */
    suspend fun getCacheState(observableDownload: ObservableDownload): CacheStateUpdate =
        withContext(Dispatchers.IO) {
            val isDownloaded = when (observableDownload) {
                is DownloadableCollection -> observableDownload.isDownloaded(context)
                is AbstractIssueKey -> issueRepository.isDownloaded(observableDownload)
                else -> false
            }
            if (isDownloaded) {
                CacheStateUpdate(
                    CacheStateUpdate.Type.INITIAL,
                    CacheState.PRESENT,
                    0,
                    0,
                    null
                )
            } else {
                CacheStateUpdate(
                    CacheStateUpdate.Type.INITIAL,
                    CacheState.ABSENT,
                    0,
                    0,
                    null
                )
            }
        }

    /**
     * This function will download an [issueKey] (both Metadata and Contents) if
     * it is not yet marked as downloaded. If it is it will just return
     * @param issueKey The key of the issue to be downloaded
     * @param priority The priority of the download
     * @param isAutomaticDownload Indicator if the download was triggered automatically
     * @throws CacheOperationFailedException You are strongly advised to catch this exception as a lot of volatile subprocess happen (I/O etc)
     */
    @Throws(CacheOperationFailedException::class)
    suspend fun downloadToCacheIfNotPresent(
        issueKey: AbstractIssueKey,
        priority: DownloadPriority = DownloadPriority.Normal,
        isAutomaticDownload: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (!issueRepository.isDownloaded(issueKey)) {
            downloadToCache(issueKey, priority, isAutomaticDownload)
        }
    }

    /**
     * This function will invoke [downloadToCache] if the provided collection is not downloaded
     *
     * @param collection The collection to be downloaded
     * @param priority The priority of the download
     * @param isAutomaticDownload Indicator if the download was triggered automatically
     * @throws CacheOperationFailedException You are strongly advised to catch this exception as a lot of volatile subprocess happen (I/O etc)
     */
    @Throws(CacheOperationFailedException::class)
    suspend fun downloadToCacheIfNotPresent(
        collection: DownloadableCollection,
        priority: DownloadPriority = DownloadPriority.Normal,
        isAutomaticDownload: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (!collection.isDownloaded(context)) {
            downloadToCache(collection, priority, isAutomaticDownload)
        }
    }

    /**
     * This function will download a [download] (both Metadata and Contents) if
     * it is not yet marked as downloaded. If it is it will just return
     * @param download The [ObservableDownload] to be downloaded
     * @param priority The priority of the download
     * @param isAutomaticDownload Indicator if the download was triggered automatically
     * @throws CacheOperationFailedException You are strongly advised to catch this exception as a lot of volatile subprocess happen (I/O etc)
     */
    private suspend fun downloadToCache(
        download: ObservableDownload,
        priority: DownloadPriority = DownloadPriority.Normal,
        isAutomaticDownload: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val tag = determineParentTag(download)
        val wrappedDownload = WrappedDownload.prepare(
            context,
            download,
            isAutomaticDownload,
            priority,
            tag
        )
        wrappedDownload.execute()
    }

    /**
     * This function will retrieve the Metadata of [ObservableDownload]
     * Only if not found in database it will download it from the API
     *
     * @param download [ObservableDownload] of which the Metadata should be retrieved
     * @return A DownloadableStub representing the retrieved Metadata
     */
    suspend fun downloadMetadataIfNotPresent(
        download: ObservableDownload
    ): DownloadableStub {
        return MetadataDownload
            .prepare(context, download, download.getDownloadTag(), allowCache = true)
            .execute()
    }

    /**
     * Download a single [FileEntry] while providing the [baseUrl]
     * @param fileEntry The [FileEntry] to download
     * @param baseUrl The base url where the [FileEntry] is to be found
     * @throws CacheOperationFailedException If anything goes wrong this exception is wrapping the cause
     */
    @Throws(CacheOperationFailedException::class)
    suspend fun downloadSingleFileIfNotDownloaded(
        fileEntry: FileEntry,
        baseUrl: String,
        priority: DownloadPriority = DownloadPriority.Normal
    ) {
        if (fileEntry.getDownloadDate(context) == null) {
            ContentDownload
                .prepare(context, fileEntry, baseUrl, priority)
                .execute()
        }
    }

    /**
     * Download a single [FileEntry] while providing the [baseUrl]
     * @param fileEntry The [FileEntry] to download
     * @param baseUrl The base url where the [FileEntry] is to be found
     * @throws CacheOperationFailedException If anything goes wrong this exception is wrapping the cause
     */
    @Throws(CacheOperationFailedException::class)
    suspend fun downloadSingleFile(
        fileEntry: FileEntry,
        baseUrl: String,
        priority: DownloadPriority = DownloadPriority.Normal
    ) {
        ContentDownload
            .prepare(context, fileEntry, baseUrl, priority)
            .execute()
    }

    /**
     * This function will download an the contents for given [DownloadableCollection] Metadata
     * it is not yet marked as downloaded. If it is it will just return
     *
     * @param collection The [ObservableDownload] of which the contents should be downloaded
     * @param priority The priority of the download. If there is another operation active with a lower priority it will bump that operation with this
     */
    internal suspend fun downloadCollectionContentIfNotPresent(
        collection: DownloadableCollection,
        priority: DownloadPriority = DownloadPriority.Normal
    ) = withContext(Dispatchers.Default) {
        if (collection.isDownloaded(context)) {
            return@withContext
        }
        val contentDownload = ContentDownload.prepare(
            context,
            collection,
            priority
        )
        contentDownload.execute()
    }

    /**
     * Retrieves the metadata of [issueKey] and delete its contents
     *
     * @param issueKey The issueKey the content of which should be deleted
     * @throws NotFoundException If no Issue matching [issueKey] was found in the database
     */
    @Throws(NotFoundException::class)
    suspend fun deleteContent(issueKey: AbstractIssueKey) = withContext(Dispatchers.IO) {
        val issue = when (issueKey) {
            is IssueKeyWithPages -> issueRepository.get(issueKey)
            is IssueKey -> issueRepository.get(issueKey)
            else -> null
        } ?: throw NotFoundException("Issue not found")
        val deletion = IssueDeletion.prepare(context, issue, determineParentTag(issueKey))
        deletion.execute()
    }

    /**
     * Delete the contents of a [collection] metadata
     *
     * @param collection The collection the content of which should be deleted
     */
    suspend fun deleteContent(collection: DownloadableCollection) = withContext(Dispatchers.IO) {
        ContentDeletion.prepare(context, collection, collection.getDownloadTag()).execute()
    }
}