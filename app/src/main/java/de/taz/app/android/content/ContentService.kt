package de.taz.app.android.content

import android.content.Context
import de.taz.app.android.METADATA_DOWNLOAD_RETRY_INDEFINITELY
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
    private val downloadDataStore = DownloadDataStore.getInstance(applicationContext)

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
        // we need to listen for either parent tag or tag with /pdf because downloads with pages
        // should also be shown as download of issue and deletion as well.
        val parentTags: List<String> =
            listOf(determineParentTag(download), download.getDownloadTag() + "/pdf")
        val tag = download.getDownloadTag()
        return cacheStatusFlow
            .filter { pair ->
                // Receive updates to both the parent operation and any discrete operation
                parentTags.any { tag -> pair.first.startsWith(tag) } || tag == pair.first
            }
            .map { it.second }
            .also {
                launch {
                    val stateUpdate: CacheStateUpdate =
                        activeCacheOperations.filterKeys { it in parentTags }.values.firstOrNull()?.state
                            ?: getCacheState(download)

                    // only emit if no status has been provided yet
                    cacheStatusFlow.onEmpty {
                        log.debug("emmiting on empty $stateUpdate")
                        cacheStatusFlow.emit(tag to stateUpdate)
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
     * This function wraps downloadToCache calls of issuePublication.
     * When calling downloadIssuePublicationToCache we do not know if [IssuePublicationWithPages] or
     * just [IssuePublication] should be downloaded.
     * That is defined in the [DownloadDataStore] with the [pdfAdditionally] flag.
     * */
    suspend fun downloadIssuePublicationToCache(
        issuePublication: AbstractIssuePublication,
        priority: DownloadPriority = DownloadPriority.Normal,
        isAutomaticDownload: Boolean = false,
        allowCache: Boolean = true
    ) {
        val download: ObservableDownload =
            if (downloadDataStore.pdfAdditionally.get()) {
                IssuePublicationWithPages(issuePublication)
            } else {
                issuePublication
            }

        downloadToCache(download, priority, isAutomaticDownload, allowCache)
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
        val wrappedDownload = WrappedDownload.prepare(
            applicationContext,
            download,
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
     * @param minStatus the minimal required [IssueStatus] - if not manually given [AuthHelper.getMinStatus] will be used
     * @return The returned object, might be a [DownloadableCollection] of any kind, [Issue], [IssueKeyWithPages]
     * or [AppInfo]
     */
    suspend fun downloadMetadata(
        download: ObservableDownload,
        maxRetries: Int = METADATA_DOWNLOAD_RETRY_INDEFINITELY,
        forceExecution: Boolean = false,
        minStatus: IssueStatus? = null,
        allowCache: Boolean = true
    ): ObservableDownload {
        return MetadataDownload
            .prepare(
                applicationContext,
                download,
                download.getDownloadTag(),
                retriesOnConnectionError = maxRetries,
                allowCache = allowCache,
                minStatus = minStatus ?: authHelper.getMinStatus()
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
    suspend fun deleteIssue(issuePublication: AbstractIssuePublication) {
        withContext(Dispatchers.IO) {
            IssueDeletion.prepare(applicationContext, issuePublication)
                .execute()
        }
    }
}