package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.DownloadableStub
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.api.models.*
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.repository.IssueKeyWithPages
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import kotlinx.coroutines.*
import java.util.*

/**
 * The download of a collection can trigger the download of other collections
 * For example [de.taz.app.android.api.models.Issue] is practically just a collection of collections
 * but also [de.taz.app.android.api.models.Section], [de.taz.app.android.api.models.Article] and [de.taz.app.android.api.models.Page]
 * can mandate [de.taz.app.android.api.models.ResourceInfo] to be downloaded.
 * It's a practical use case wanting to listen in on the download of a collection *and* it's dependents.
 *
 * @param context An android [Context] object
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
         * @param priority The download priority of this operation
         * @param tag The tag to be used for this operation
         */
        suspend fun prepare(
            applicationContext: Context,
            parent: ObservableDownload,
            isAutomaticDownload: Boolean,
            priority: DownloadPriority,
            tag: String
        ): WrappedDownload = withContext(Dispatchers.Main) {
            WrappedDownload(
                applicationContext,
                parent,
                emptyList(),
                isAutomaticDownload,
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
    override suspend fun doWork() = withContext(Dispatchers.IO) {
        notifyStart()
        // Before downloading content we _always_ download the corresponding metadata.
        // Metadata can get stale and the references to the content will be broken
        val metadataDownload = MetadataDownload.prepare(
            applicationContext,
            parent,
            parent.getDownloadTag() // attention! don't use the tag of this wrapping operation otherwise there'll be a name conflict
        )
        addItem(
            SubOperationCacheItem(metadataDownload.tag, { priority }, metadataDownload),
        )
        val parentCollection = metadataDownload.execute()
        // corner case: If we invoke this function with an IssueKeyWithPages we need to cast
        // the Issue metadata to a IssueWithPages to also download the pdf pages
        val issueWithPages = if (parent is IssueKeyWithPages && parentCollection is Issue) {
            IssueWithPages(parentCollection)
        } else {
            null
        }
        val dependentCollections = try {
            resolveCollections(issueWithPages ?: parentCollection)
        } catch (originalException: Exception) {
            val exception = CacheOperationFailedException(
                "Error while retrieving metadata",
                originalException
            )
            notifyFailedItem(exception)
            notifyFailiure(exception)
            throw exception
        }

        val issueDownloadNotifier = if (parent is AbstractIssue) {
            IssueDownloadNotifier(applicationContext, parent.issueKey, isAutomaticDownload)
        } else null

        issueDownloadNotifier?.start()

        val subOperationCacheItems = dependentCollections
            .filter { !it.isDownloaded(applicationContext) }
            .map {
                val contentDownload = ContentDownload.prepare(
                    applicationContext,
                    it,
                    priority
                )
                SubOperationCacheItem(
                    it.getDownloadTag(),
                    { priority },
                    contentDownload
                )
            }

        // Add all content downloads to the items
        addItems(subOperationCacheItems)

        var errorCount = 0
        // Launch each collection download in parallel and notify each item
        subOperationCacheItems.map {
            launch(Dispatchers.IO) {
                try {
                    it.subOperation.execute()
                    notifySuccessfulItem()
                } catch (e: Exception) {
                    errorCount++
                    notifyFailedItem(e)
                }
            }
        }.joinAll()

        issueDownloadNotifier?.stop()
        if (errorCount == 0) {
            parentCollection.setDownloadDate(Date(), applicationContext)
            notifySuccess(Unit)
        } else {
            val exception = CacheOperationFailedException(
                "One or more sub operations failed"
            )
            notifyFailiure(
                exception
            )
            throw exception
        }
    }

    /**
     * Resolve all [DownloadableCollection]s that [download] depends on.
     * In the case of an [Issue] that's most likely its [Section]s and [Article]s.
     * Any [download] might depend on an appropriate [ResourceInfo]
     */
    private suspend fun resolveCollections(download: DownloadableStub): List<DownloadableCollection> {
        // Get the required resource info - if it already is marked as downloaded do not add it to the set of required items
        val requiredResourceInfo = getRequiredResourceInfo(download)?.let {
            if (it.isDownloaded(applicationContext)) null else it
        }

        return (when (download) {
            is Issue -> {
                setOfNotNull(
                    download.imprint
                ) + download.sectionList + download.getArticles() + download.moment
            }
            is IssueWithPages -> {
                // Issue with pages also needs the pageList!
                setOfNotNull(
                    download.imprint
                ) + download.sectionList + download.getArticles() + download.moment + download.pageList
            }
            is Article, is Page, is Section, is Moment, is ResourceInfo -> setOf(download as DownloadableCollection)
            else -> throw IllegalArgumentException("Don\'t know how dow download $download")
        } + setOfNotNull(requiredResourceInfo)).toList()
    }

    /**
     * Determines the required [ResourceInfo] for [collection]
     *
     * @param collection The collection of which a required [ResourceInfo] should be found
     * @return A ResourceInfo object, only if [collection] depends on one.
     */
    private suspend fun getRequiredResourceInfo(collection: ObservableDownload): ResourceInfo? {
        val minResourceVersion = when (collection) {
            is IssueOperations -> collection.minResourceVersion
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
     * @param minVersion The minimum requred version of [ResourceInfo]
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
}