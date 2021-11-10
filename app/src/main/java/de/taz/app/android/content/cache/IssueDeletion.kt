package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.api.models.AbstractIssue
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.repository.ArticleRepository

/**
 * A [CacheOperation] composed of a [ContentDeletion] and subsequent [MetadataDeletion]
 * of an [AbstractIssue].
 *
 * @param applicationContext An android application context object
 * @param issue The issue that should be deleted (both contents and metadata)
 * @param tag The tag on which this operation should be registered
 */
class IssueDeletion(
    applicationContext: Context,
    val items: List<SubOperationCacheItem>,
    val issue: AbstractIssue,
    tag: String
) : CacheOperation<SubOperationCacheItem, Unit>(
    applicationContext,
    items,
    CacheState.ABSENT,
    tag
) {
    override val loadingState: CacheState = CacheState.DELETING_CONTENT
    private val articleRepository = ArticleRepository.getInstance(applicationContext)

    companion object {
        /**
         * @param applicationContext An android application context object
         * @param issue The issue that should be deleted (both contents and metadata)
         * @param tag The tag on which this operation should be registered
         */
        fun prepare(
            applicationContext: Context,
            issue: AbstractIssue,
            tag: String
        ): IssueDeletion {

            return IssueDeletion(
                applicationContext,
                emptyList(),
                issue,
                tag
            )
        }
    }

    override suspend fun doWork() {
        notifyStart()
        issue.setDownloadDate(null, applicationContext)
        val collectionsToDeleteContent =
            listOfNotNull(issue.imprint) +
                    issue.sectionList +
                    issue.getArticles().filter { !it.bookmarked } +
                    issue.pageList
        val issueMetadataDeletionCacheItem = if (
            articleRepository.getBookmarkedArticleStubsForIssue(issue.issueKey).isEmpty()
        ) {
            val deletion = MetadataDeletion.prepare(applicationContext, issue)
            SubOperationCacheItem(
                deletion.tag,
                { DownloadPriority.Normal },
                deletion
            )
        } else {
            log.warn("Not deleting issue metadata of $issue as it's still needed by bookmarked articles")
            null
        }
        val contentDeletionCacheItems = collectionsToDeleteContent
            .map {
                SubOperationCacheItem(
                    it.getDownloadTag(),
                    { DownloadPriority.Normal },
                    ContentDeletion.prepare(
                        applicationContext,
                        it,
                        it.getDownloadTag()
                    )
                )
            }
        addItems(contentDeletionCacheItems)

        // If we are going to delete the metadata add it here too
        issueMetadataDeletionCacheItem?.let { addItem(issueMetadataDeletionCacheItem) }

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
            issueMetadataDeletionCacheItem?.subOperation?.let { metadataDeletion ->
                try {
                    metadataDeletion.execute()
                    notifySuccessfulItem()
                } catch (e: Exception) {
                    notifyFailedItem(e)
                    throw CacheOperationFailedException("Deleting the metadata for issue ${issue.issueKey}")
                }
            }
        }
        checkIfItemsCompleteAndNotifyResult(Unit)
    }
}