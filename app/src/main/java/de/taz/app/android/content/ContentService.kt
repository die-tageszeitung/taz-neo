package de.taz.app.android.content

import android.content.Context
import de.taz.app.android.METADATA_DOWNLOAD_DEFAULT_RETRIES
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.DownloadableStub
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.api.models.*
import de.taz.app.android.content.cache.*
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.download.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The [ContentService] provides easy-to-use functions to download content (cache) for
 * any [ObservableDownload]. It should provide a simple and robust api for both
 * managing the content cache and listen to updates to that cache.
 *
 * @param applicationContext The Android [Context]
 */
@Mockable
class ContentService(
    private val applicationContext: Context
) {

    companion object : SingletonHolder<ContentService, Context>(::ContentService)

    private val issueRepository = IssueRepository.getInstance(applicationContext)
    private val appInfoRepository = AppInfoRepository.getInstance(applicationContext)
    private val authHelper = AuthHelper.getInstance(applicationContext)
    private val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)
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
    suspend fun getCacheState(
        observableDownload: ObservableDownload,
    ): CacheStateUpdate =
        withContext(Dispatchers.IO) {
            val isDownloaded = when (observableDownload) {
                is DownloadableCollection -> observableDownload.isDownloaded(applicationContext)
                is AppInfoKey -> appInfoRepository.get() != null
                is ResourceInfoKey -> resourceInfoRepository.getStub()?.resourceVersion ?: -1 > observableDownload.minVersion
                is MomentKey -> momentRepository.isDownloaded(observableDownload)
                is AbstractIssueKey -> issueRepository.isDownloaded(observableDownload)
                is AbstractIssuePublication -> issueRepository.getMostValuableIssueKeyForPublication(
                    observableDownload
                )?.let {
                    if (it.status >= authHelper.getMinStatus()) issueRepository.isDownloaded(it)
                    else false
                } ?: false
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
     * This function will download a [download] (both Metadata and Contents) if
     * it is not yet marked as downloaded. If it is it will just return
     * @param download The [ObservableDownload] to be downloaded
     * @param priority The priority of the download
     * @param isAutomaticDownload Indicator if the download was triggered automatically
     * @param allowCache Indicate whether cache should be ignored
     * @throws CacheOperationFailedException You are strongly advised to catch this exception as a lot of volatile subprocess happen (I/O etc)
     */
    suspend fun downloadToCache(
        download: ObservableDownload,
        priority: DownloadPriority = DownloadPriority.Normal,
        isAutomaticDownload: Boolean = false,
        allowCache: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val tag = determineParentTag(download)
        val toDownload =
            if (download is IssuePublication && DownloadDataStore.getInstance(applicationContext).pdfAdditionally.get()) {
                IssuePublicationWithPages(download)
            } else download
        val wrappedDownload = WrappedDownload.prepare(
            applicationContext,
            toDownload,
            isAutomaticDownload,
            allowCache,
            priority,
            tag
        )
        wrappedDownload.execute()
    }

    /**
     * This function will retrieve the Metadata of [ObservableDownload]
     * Depending on [allowCache] parameter will return a cached version of the Metadata
     *
     * @param download [ObservableDownload] of which the Metadata should be retrieved
     * @param maxRetries The amount of retries on connection errors
     * @param forceExecution If this operation should be executed regardless if an equal operation has already been started
     * @return The returned object, might be a [DownloadableCollection] of any kind, [Issue], [IssueKeyWithPages]
     * or [AppInfo]
     */
    suspend fun downloadMetadata(
        download: ObservableDownload,
        maxRetries: Int = METADATA_DOWNLOAD_DEFAULT_RETRIES,
        forceExecution: Boolean = false,
        minStatus: IssueStatus = IssueStatus.public,
        allowCache: Boolean = true
    ): ObservableDownload {
        return MetadataDownload
            .prepare(
                applicationContext,
                download,
                download.getDownloadTag(),
                retriesOnConnectionError = maxRetries,
                allowCache = allowCache,
                minStatus = minStatus
            )
            .execute(forceExecution = forceExecution)
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
        if (fileEntry.getDownloadDate(applicationContext) == null) {
            ContentDownload
                .prepare(applicationContext, fileEntry, baseUrl, priority)
                .execute()
        }
    }

    /**
     * Deletes all issues on a publication date [issuePublication] and their contents
     *
     * @param issuePublication The issueKey the content of which should be deleted
     */
    @Throws(NotFoundException::class)
    suspend fun deleteIssue(issuePublication: AbstractIssuePublication) = withContext(Dispatchers.IO) {

        val deletion = IssueDeletion.prepare(
            applicationContext,
            issuePublication,
            determineParentTag(issuePublication)
        )
        deletion.execute()
    }
}