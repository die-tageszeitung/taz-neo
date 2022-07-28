package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.api.models.*
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.persistence.repository.IssueKey

/**
 * An operation deleting the Metadata of given object
 *
 * @param applicationContext An android application context object
 * @param tag The tag on which this operation should be registered
 * @param download The object of which the metadata should be deleted
 */
class MetadataDeletion(
    applicationContext: Context,
    items: List<MetadataCacheItem>,
    tag: String,
    val download: ObservableDownload
): CacheOperation<MetadataCacheItem, Unit>(
    applicationContext, items, CacheState.ABSENT, tag
) {
    override val loadingState: CacheState = CacheState.DELETING_METADATA
    companion object {
        /**
         * Creates a [MetadataDeletion]
         * @param applicationContext An android application context object
         * @param download The object of which the metadata should be deleted
         */
        fun prepare(
            applicationContext: Context,
            download: ObservableDownload
        ): MetadataDeletion {
            val metadataDeletionCacheItem = MetadataCacheItem(
                download.getDownloadTag(),
                { DownloadPriority.Normal },
                download.getDownloadTag()
            )
            return MetadataDeletion(
                applicationContext,
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
                is AbstractIssueKey -> issueRepository.delete(IssueKey(download))
                is Issue -> issueRepository.delete(download)
                is IssueStub -> issueRepository.delete(download.issueKey)
                is IssueWithPages -> issueRepository.delete(Issue(download))
                else -> {
                    throw IllegalArgumentException("A ${download::class} is not allowed to be deleted")
                }
            }.also {
                notifySuccessfulItem()
                notifySuccess(it)
            }
        } catch (e: Exception) {
            notifyFailedItem(e)
            notifyFailure(e)
            throw CacheOperationFailedException("Metadata deletion of ${download.getDownloadTag()} failed", e)
        }
    }
}
