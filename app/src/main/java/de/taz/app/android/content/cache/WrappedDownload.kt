package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.METADATA_DOWNLOAD_RETRY_INDEFINITELY
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.DownloadableStub
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.api.models.*
import de.taz.app.android.content.ContentService
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.sentry.SentryWrapper
import kotlinx.coroutines.*
import java.util.*
import kotlin.collections.LinkedHashSet

/**
 * The download of a collection can trigger the download of other collections
 * For example [de.taz.app.android.api.models.Issue] is practically just a collection of collections
 * but also [de.taz.app.android.api.models.Section], [de.taz.app.android.api.models.Article] and [de.taz.app.android.api.models.Page]
 * can mandate [de.taz.app.android.api.models.ResourceInfo] to be downloaded.
 * It's a practical use case wanting to listen in on the download of a collection *and* it's dependents.
 *
 * @param applicationContext An android [Context] object
 * @param parent The [ObservableDownload] that should be downloaded after this operation
 * @param isAutomaticDownload Indicator whether this download was triggered automatically
 * @param priority The download priority of this operation
 * @param tag The tag to be used for this operation
 */
class WrappedDownload(
    applicationContext: Context,
    val parent: ObservableDownload,
    val items: List<SubOperationCacheItem>,
    private val isAutomaticDownload: Boolean,
    private val allowCache: Boolean,
    priority: DownloadPriority,
    tag: String
) : CacheOperation<SubOperationCacheItem, Unit>(
    applicationContext,
    items,
    CacheState.PRESENT,
    tag,
    priority
) {
    override val loadingState: CacheState = CacheState.LOADING_CONTENT
    private val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)
    private val apiService = ApiService.getInstance(applicationContext)

    companion object {
        /**
         * Create a [WrappedDownload] object
         *
         * @param applicationContext An android application [Context] object
         * @param parent The [ObservableDownload] that should be downloaded after this operation
         * @param isAutomaticDownload Indicator whether this download was triggered automatically
         * @param allowCache Whether this download should skip or take cache
         * @param priority The download priority of this operation
         * @param tag The tag to be used for this operation
         */
        fun prepare(
            applicationContext: Context,
            parent: ObservableDownload,
            isAutomaticDownload: Boolean,
            allowCache: Boolean,
            priority: DownloadPriority,
            tag: String
        ): WrappedDownload {
            if (parent is AbstractIssueKey) {
                error("It is not allowed to download issues by IssueKey as " +
                        "the DownloadObserver is expecting tags without a status." +
                        "Please use an IssuePublication to trigger the issue download.")
            }

            return WrappedDownload(
                applicationContext,
                parent,
                emptyList(),
                isAutomaticDownload,
                allowCache,
                priority,
                tag
            )
        }
    }

    /**
     * [WrappedDownload] will download the metadata of [parent] and will decide
     * which other [ObservableDownload]s it might be depending on.
     * It will then proceed to start [ContentDownload]s for both the contents of the [parent] and
     * the resolved dependencies
     */
    override suspend fun doWork() {
        notifyStart()
        val contentService = ContentService.getInstance(applicationContext)

        // For a wrapped download a successful cache hit is if the download and it's content is
        // complete, so we use the getCacheState function
        if (allowCache && contentService.isPresent(parent)) {
            notifySuccess(Unit)
            return
        }

        // Before downloading content we _always_ download the corresponding metadata.
        // Metadata can get stale and the references to the content will be broken
        val metadataDownload = MetadataDownload.prepare(
            applicationContext,
            parent,
            parent.getDownloadTag(), // attention! don't use the tag of this wrapping operation otherwise there'll be a name conflict,
            retriesOnConnectionError = METADATA_DOWNLOAD_RETRY_INDEFINITELY
        )

        addItem(
            SubOperationCacheItem(metadataDownload.tag, { priority }, metadataDownload),
        )
        val parentCollection = try {
            metadataDownload.execute()
        } catch (originalException: Exception) {
            val exception =
                CacheOperationFailedException("Retrieving metadata failed", originalException)
            notifyFailure(
                exception
            )
            throw exception
        }

        val dependentCollections = try {
            resolveCollections(parentCollection)
        } catch (originalException: Exception) {
            val exception = CacheOperationFailedException(
                "Error while retrieving metadata",
                originalException
            )
            notifyFailedItem(exception)
            notifyFailure(exception)
            throw exception
        }

        val issueDownloadNotifier = when (parentCollection) {
            is AbstractIssue -> IssueDownloadNotifier(
                applicationContext,
                parentCollection.issueKey,
                isAutomaticDownload
            )
            else -> null
        }

        issueDownloadNotifier?.start()

        val subOperationCacheItems = dependentCollections
            .filter { !it.isDownloaded(applicationContext) }
            .map { createSubOperationCacheItem(it) }

        // Add all content downloads to the items
        addItems(subOperationCacheItems)

        var errorCount = 0
        // Launch each collection download in parallel and notify each item
        coroutineScope {
            subOperationCacheItems.map {
                launch {
                    try {
                        it.subOperation.execute()
                        notifySuccessfulItem()
                    } catch (e: Exception) {
                        errorCount++
                        notifyFailedItem(e)
                        log.warn(
                            "Exception during processing a WrappedDownload of ${parent.getDownloadTag()}",
                            e
                        )
                        SentryWrapper.captureException(e)
                    }
                }
            }.joinAll()
        }
        issueDownloadNotifier?.stop()
        if (errorCount == 0) {
            if (parentCollection is DownloadableStub) {
                parentCollection.setDownloadDate(Date(), applicationContext)
            }
            notifySuccess(Unit)
        } else {
            val exception = CacheOperationFailedException(
                "One or more sub operations failed"
            )
            notifyFailure(
                exception
            )
            throw exception
        }
    }

    private suspend fun createSubOperationCacheItem(collection: DownloadableCollection): SubOperationCacheItem {
        val contentDownload = ContentDownload.prepare(
            applicationContext,
            collection,
            priority
        )
        return SubOperationCacheItem(
            collection.getDownloadTag(),
            { priority },
            contentDownload
        )
    }

    /**
     * Resolve all [DownloadableCollection]s that [download] depends on.
     * In the case of an [Issue] that's most likely its [Section]s and [Article]s.
     * Any [download] might depend on an appropriate [ResourceInfo]
     */
    private suspend fun resolveCollections(download: ObservableDownload): List<DownloadableCollection> {
        // LinkedHasSets keep the insertion order of the items,
        // which helps us to have a little better fine graining within a collection.
        // Note that the order of the this list will not be kept 100% by the download manager as is
        // starting coroutines for each download in parallel which might get picked up in a different order.    
        val downloadDependencies = LinkedHashSet<DownloadableCollection>()

        // Get the required resource info - if it already is marked as downloaded do not add it to the set of required items
        getRequiredResourceInfo(download)?.let {
            if (!it.isDownloaded(applicationContext)) {
                downloadDependencies.add(it)
            }
        }


        when (download) {
            is Issue -> {
                downloadDependencies.apply {
                    addAll(download.sectionList)
                    addAll(download.getArticles())
                    add(download.moment)
                    download.imprint?.let {
                        add(it)
                    }
                }
            }
            is IssueWithPages -> {
                // Issue with pages also needs the pageList!
                downloadDependencies.apply {
                    addAll(download.pageList)
                    addAll(download.sectionList)
                    addAll(download.getArticles())
                    add(download.moment)
                    download.imprint?.let {
                        add(it)
                    }
                }
                log.error("pages: ${download.pageList.withIndex().joinToString { (i, p) -> "$i:${p.pagePdf.name}" }}")
            }
            // AppInfo has no collection
            is AppInfo -> Unit
            is Article,
            is Page,
            is Section,
            is Moment,
            is ResourceInfo -> {
                downloadDependencies.add(download as DownloadableCollection)
            }
            else -> throw IllegalArgumentException("Don't know how to download $download")
        }


        return downloadDependencies.toList()
    }

    /**
     * Determines the required [ResourceInfo] for [collection]
     *
     * @param collection The collection of which a required [ResourceInfo] should be found
     * @return A ResourceInfo object, only if [collection] depends on one.
     */
    private suspend fun getRequiredResourceInfo(collection: ObservableDownload): ResourceInfo? {
        val minResourceVersion = when (collection) {
            is IssueOperations -> return getNewestResourceInfo() // Always get the newest ResourceInfo
            is Article -> issueRepository.getIssueStubForArticle(collection.key)?.minResourceVersion
            is Section -> issueRepository.getIssueStubForSection(collection.key)?.minResourceVersion
            else -> null
        }

        return minResourceVersion?.let { getResourceInfo(it) }
    }

    /**
     * Get the latest cached [ResourceInfo]. If that one has a lower version than [minVersion]
     * query the server for a new one
     *
     * @param minVersion The minimum required version of [ResourceInfo]
     */
    private suspend fun getResourceInfo(minVersion: Int): ResourceInfo {
        val currentResourceInfo = resourceInfoRepository.getNewest()
        return if (currentResourceInfo == null || currentResourceInfo.resourceVersion < minVersion) {
            val newResourceInfo = apiService.getResourceInfo()
            resourceInfoRepository.save(newResourceInfo)
            newResourceInfo
        } else {
            currentResourceInfo
        }
    }

    /**
     * Always query the server for the latest [ResourceInfo] and return that.
     */
    private suspend fun getNewestResourceInfo(): ResourceInfo {
        val newResourceInfo = apiService.getResourceInfo()
        resourceInfoRepository.save(newResourceInfo)
        return newResourceInfo
    }
}