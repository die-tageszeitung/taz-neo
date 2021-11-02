package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.api.models.AbstractIssue
import de.taz.app.android.content.ContentService
import de.taz.app.android.download.DownloadPriority

/**
 * A [CacheOperation] composed of a [ContentDeletion] and subsequent [MetadataDeletion]
 * of an [AbstractIssue].
 *
 * @param context An android context object
 * @param issue The issue that should be deleted (both contents and metadata)
 * @param tag The tag on which this operation should be registered
 */
class IssueDeletion(
    context: Context,
    val items: List<SubOperationCacheItem>,
    val issue: AbstractIssue,
    tag: String
) : CacheOperation<SubOperationCacheItem, Unit>(
    context,
    items,
    CacheState.ABSENT,
    tag
) {
    override val loadingState: CacheState = CacheState.DELETING_CONTENT

    companion object {
        /**
         * @param context An android context object
         * @param issue The issue that should be deleted (both contents and metadata)
         * @param tag The tag on which this operation should be registered
         */
        suspend fun prepare(
            context: Context,
            issue: AbstractIssue,
            tag: String
        ): IssueDeletion {

            return IssueDeletion(
                context,
                emptyList(),
                issue,
                tag
            )
        }
    }

    override suspend fun doWork() {
        notifyStart()
        issue.setDownloadDate(null, context)
        val collectionsToDeleteContent =
            listOfNotNull(issue.imprint) + issue.sectionList + issue.getArticles()
        val issueMetadataDeletion = MetadataDeletion.prepare(context, issue)
        val issueMetadataDeletionCacheItem = SubOperationCacheItem(
            issueMetadataDeletion.tag,
            { DownloadPriority.Normal },
            issueMetadataDeletion
        )
        val contentDeletionCacheItems = collectionsToDeleteContent
            .map {
                SubOperationCacheItem(
                    it.getDownloadTag(),
                    { DownloadPriority.Normal },
                    ContentDeletion.prepare(
                        context,
                        it,
                        it.getDownloadTag()
                    )
                )
            }
        addItems(contentDeletionCacheItems + issueMetadataDeletionCacheItem)

        contentDeletionCacheItems.forEach {
            try {
                it.subOperation.execute()
                notifySuccessfulItem()
            } catch (e: Exception) {
                notifyFailedItem(e)
            }
        }

        if (failedCount > 0) {
            // If we encoutered errors while deleting content skip deleting metadata, just indicate another failed item and throw exception
            notifyFailedItem(CacheOperationFailedException("Operation aborted due to previous errors"))
        } else {
            try {
                issueMetadataDeletion.execute()
                notifySuccessfulItem()
            } catch (e: Exception) {
                notifyFailedItem(e)
                throw CacheOperationFailedException("Deleting the metadata for issue ${issue.issueKey}")
            }
        }
        checkIfItemsCompleteAndNotifyResult(Unit)
    }
}