package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.api.models.*
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.repository.AbstractIssueKey

/**
 * An operation deleting the Metadata of given object
 *
 * @param context An android context object
 * @param tag The tag on which this operation should be registered
 * @param download The object of which the metadata should be deleted
 */
class MetadataDeletion(
    context: Context,
    items: List<MetadataCacheItem>,
    tag: String,
    val download: ObservableDownload
): CacheOperation<MetadataCacheItem, Unit>(
    context, items, CacheState.ABSENT, tag
) {
    override val loadingState: CacheState = CacheState.DELETING_METADATA
    companion object {
        /**
         * Creates a [MetadataDeletion]
         * @param context An android context object
         * @param download The object of which the metadata should be deleted
         */
        fun prepare(
            context: Context,
            download: ObservableDownload
        ): MetadataDeletion {
            val metadataDeletionCacheItem = MetadataCacheItem(
                download.getDownloadTag(),
                { DownloadPriority.Normal },
                download.getDownloadTag()
            )
            return MetadataDeletion(
                context,
                listOf(metadataDeletionCacheItem),
                download.getDownloadTag(),
                download
            )
        }
    }

    override suspend fun doWork() {
        notifyStart()
        try {
            when (download) {
                is AbstractIssueKey -> dataService.deleteIssue(download)
                is Issue -> dataService.deleteIssue(download)
                is IssueStub -> dataService.deleteIssue(download)
                is IssueWithPages -> dataService.deleteIssue(Issue(download))
                else -> {
                    throw IllegalArgumentException("A ${download::class} is not allowed to be deleted")
                }
            }.also {
                notifySuccessfulItem()
                notifySuccess(it)
            }
        } catch (e: Exception) {
            notifyFailedItem(e)
            notifyFailiure(e)
            throw CacheOperationFailedException("Metadata deletion of ${download.getDownloadTag()} failed", e)
        }
    }
}
