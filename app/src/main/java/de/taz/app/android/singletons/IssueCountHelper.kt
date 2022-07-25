package de.taz.app.android.singletons

import android.content.Context
import androidx.annotation.VisibleForTesting
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.content.ContentService
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Singleton ensuring we only have the defined maximal number of issues downloaded
 */
@Mockable
class IssueCountHelper @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) constructor(
    applicationContext: Context,
) {

    companion object : SingletonHolder<IssueCountHelper, Context>(::IssueCountHelper)

    private val contentService = ContentService.getInstance(applicationContext)
    private val issueRepository = IssueRepository.getInstance(applicationContext)
    private val storageDataStore = StorageDataStore.getInstance(applicationContext)

    private val keepIssuesNumberFlow = storageDataStore.keepIssuesNumber.asFlow()
    private val downloadedIssueCountFlow = issueRepository.getDownloadedIssuesCountFlow()

    private val ensureCountLock = Mutex()

    init {
        launch {
            // if number of downloaded issues or the number of desired issues changes
            // we need to check if we are in the desired bounds
            combine(keepIssuesNumberFlow, downloadedIssueCountFlow) { max, downloaded ->
                max to downloaded
            }.collect { (max, downloaded) -> ensureIssueCount(max, downloaded) }
        }
    }

    /**
     * Check whether there are more issues downloaded then desired and delete one issue if this is
     * the case
     */
    private suspend fun ensureIssueCount(max: Int, downloaded: Int) = ensureCountLock.withLock {
        if (downloaded > max) {
            issueRepository.getIssueStubToDelete()?.let {
                contentService.deleteIssue(IssuePublication(it.issueKey))
            }
        }
    }
}
