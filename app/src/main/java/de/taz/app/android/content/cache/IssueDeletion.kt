package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.api.models.AbstractIssue
import de.taz.app.android.api.models.IssueWithPages
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.repository.AbstractIssuePublication
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
    val issuePublication: AbstractIssuePublication,
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
         * @param issuePublication The issue that should be deleted (both contents and metadata)
         * @param tag The tag on which this operation should be registered
         */
        fun prepare(
            applicationContext: Context,
            issuePublication: AbstractIssuePublication,
            tag: String
        ): IssueDeletion {

            return IssueDeletion(
                applicationContext,
                emptyList(),
                issuePublication,
                tag
            )
        }
    }

    override suspend fun doWork() {
        notifyStart()
        val issues = issueRepository.getIssuesByFeedAndDate(
            issuePublication.feedName,
            issuePublication.date
        )
        val contentDeletionCacheItems: MutableList<SubOperationCacheItem> = mutableListOf()
        val metadataDeletionCacheItems: MutableList<SubOperationCacheItem> = mutableListOf()

        for (issue in issues) {
            // Maybe the issue is IssueWithPages, we do not know at this moment,
            // so set download date to null of IssueWithPages will set both downloadDates to null
            issueRepository.setDownloadDate(IssueWithPages(issue), null)
            val collectionsToDeleteContent =
                listOfNotNull(issue.imprint) +
                        issue.sectionList +
                        issue.getArticles().filter { !it.bookmarked } +
                        issue.pageList

            // If no bookmarked article is attached, delete metadata, too
            if (articleRepository.getBookmarkedArticleStubsForIssue(issue.issueKey).isEmpty()) {
                val deletion = MetadataDeletion.prepare(applicationContext, issue)
                SubOperationCacheItem(
                    deletion.tag,
                    { DownloadPriority.Normal },
                    deletion
                ).also {
                    metadataDeletionCacheItems.add(it)
                    addItem(it)
                }
            }

            contentDeletionCacheItems.addAll(collectionsToDeleteContent
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
                })
        }

        addItems(contentDeletionCacheItems)
        contentDeletionCacheItems.forEach {
            try {
                it.subOperation.execute()
                notifySuccessfulItem()
            } catch (e: Exception) {
                notifyFailedItem(e)
            }
        }

        if (failedCount > 0) {
            // If we encountered errors while deleting content skip deleting metadata, just indicate another failed item and throw exception
            notifyFailedItem(CacheOperationFailedException("Operation aborted due to previous errors"))
        } else {
            for (item in metadataDeletionCacheItems) {
                try {
                    item.subOperation.execute()
                    notifySuccessfulItem()
                } catch (e: Exception) {
                    notifyFailedItem(e)
                    throw CacheOperationFailedException("Deleting the metadata for issue publication $issuePublication failed", e)
                }
            }

        }
        checkIfItemsCompleteAndNotifyResult(Unit)
    }
}