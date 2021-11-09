package de.taz.app.android.singletons

import android.content.Context
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Moment
import de.taz.app.android.api.models.Page
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CannotDetermineBaseUrlException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class StoragePathService private constructor(private val applicationContext: Context) {
    companion object : SingletonHolder<StoragePathService, Context>(::StoragePathService)

    private val log by Log
    private val issueRepository = IssueRepository.getInstance(applicationContext)

    /**
     * Determine the base url of a collection by resolving the issue or determining it's a global
     * asset.
     * A more elegant solution might be to actually store the origin url in the FileEntry,
     * so we don't need this brittle code
     */
    suspend fun determineBaseUrl(
        fileEntry: FileEntry,
        collection: DownloadableCollection? = null,
    ): String = withContext(Dispatchers.IO) {
        val dataService = DataService.getInstance(applicationContext)
        when (fileEntry.storageType) {
            StorageType.global -> {
                dataService.getAppInfo().globalBaseUrl
            }
            StorageType.resource -> {
                dataService.getResourceInfo().resourceBaseUrl
            }
            StorageType.issue -> {
                when (collection) {
                    is Moment -> if (collection.baseUrl.isNotEmpty()) {
                        collection.baseUrl
                    } else {
                        // TODO: We migrated baseUrl from Issue in earlier versions to make the Moment standalone. To monitor this volatile migration report any inconsistency
                        // Can be removed if no problem occurs
                        val hint =
                            "Moment.baseUrl was not properly migrated for ${collection.getDownloadTag()}"
                        Sentry.captureMessage(hint)
                        log.warn(hint)
                        issueRepository.get(
                            IssueKey(
                                collection.issueFeedName,
                                collection.issueDate,
                                collection.issueStatus
                            )
                        )?.baseUrl
                            ?: throw CannotDetermineBaseUrlException("Could not determine base url for ${collection.getDownloadTag()}")
                    }
                    is IssueOperations -> collection.baseUrl
                    is Page -> collection.baseUrl
                    is WebViewDisplayable -> collection.getIssueStub(applicationContext)?.baseUrl
                        ?: throw CannotDetermineBaseUrlException("${collection.key} has no issue")
                    else -> throw CannotDetermineBaseUrlException("$collection is not an issue but tried to download a file with storage type issue: ${fileEntry.name}")
                }
            }
        }
    }
}