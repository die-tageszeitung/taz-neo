package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.DownloadableStub
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.api.models.IssueWithPages
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueKeyWithPages
import de.taz.app.android.persistence.repository.IssuePublication

/**
 * An operation downloading the Metadata of a given object
 *
 * @param applicationContext An android application context object
 * @param tag The tag on which this operation should be registered
 * @param download The object of which the metadata should be deleted
 * @param allowCache If the cache of Metadata should be used if existend (download will be skipped then)
 * @param retryOnConnectionError Specify if a reqeuest should pause and retry if a connection error is encountered
 */
class MetadataDownload(
    applicationContext: Context,
    tag: String,
    val download: ObservableDownload,
    private val allowCache: Boolean,
    private val retryOnConnectionError: Boolean
): CacheOperation<MetadataCacheItem, DownloadableStub>(
    applicationContext, emptyList(), CacheState.METADATA_PRESENT, tag
) {
    override val loadingState: CacheState = CacheState.LOADING_METADATA

    companion object {
        /**
         * Creates a [MetadataDownload] of a given object
         *
         * @param context An android context object
         * @param tag The tag on which this operation should be registered
         * @param download The object of which the metadata should be deleted
         * @param allowCache If the cache of Metadata should be used if existend (download will be skipped then)
         */
        fun prepare(
            context: Context,
            download: ObservableDownload,
            tag: String,
            allowCache: Boolean = false,
            retryOnConnectionError: Boolean = true
        ): MetadataDownload {
            return MetadataDownload(
                context,
                tag,
                download,
                allowCache,
                retryOnConnectionError
            )
        }
    }

    override suspend fun doWork(): DownloadableStub {
        notifyStart()
        // If we were supplied with an issue key or an issue we want to fetch the newest version of it before
        // downloading, as stale data is bad. In any other collection we cant and will use whatever is in DB
        return when (download) {
            is IssueKey -> dataService.getIssue(
                IssuePublication(download),
                allowCache = allowCache,
                retryOnFailure = retryOnConnectionError
            ) { notifyBadConnection() }
            is IssueKeyWithPages -> IssueWithPages(dataService.getIssue(
                IssuePublication(download),
                allowCache = allowCache,
                retryOnFailure = retryOnConnectionError
            ) { notifyBadConnection() })
            is IssueOperations -> dataService.getIssue(
                IssuePublication(download.issueKey),
                allowCache = allowCache,
                retryOnFailure = retryOnConnectionError
            ) { notifyBadConnection() }
            // Other collections like Articles, Sections, Pages are not directly queryable,
            // so updates only are possible by requerying the whole issue
            is DownloadableCollection -> download
            else -> {
                val exception = IllegalArgumentException("MetadataDownload is only valid with DownloadableCollection or AbstractIssueKey")
                notifyFailiure(exception)
                throw CacheOperationFailedException("MetadataDownload failed because of bad arguments", exception)
            }
        }.also {
            notifySuccess(it)
        }
    }
}