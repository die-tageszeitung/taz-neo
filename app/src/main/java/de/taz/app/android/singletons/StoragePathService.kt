package de.taz.app.android.singletons

import android.content.Context
import de.taz.app.android.api.models.StorageType
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.*
import de.taz.app.android.content.ContentService
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CannotDetermineBaseUrlException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class StoragePathService private constructor(private val applicationContext: Context) {
    companion object : SingletonHolder<StoragePathService, Context>(::StoragePathService)

    private val issueRepository = IssueRepository.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)

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
        when (fileEntry.storageType) {
            StorageType.global -> {
                (contentService.downloadMetadata(
                    AppInfoKey(),
                    maxRetries = -1 // retry indefinitely
                ) as AppInfo).globalBaseUrl
            }
            StorageType.resource -> {
                (contentService.downloadMetadata(
                    ResourceInfoKey(-1),
                    maxRetries = -1 // retry indefinitely
                ) as ResourceInfo).resourceBaseUrl
            }
            StorageType.issue -> {
                when (collection) {
                    is Moment -> collection.baseUrl.ifEmpty {
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

    /**
     * Determine the base url of a [FileEntry].
     */
    suspend fun determineBaseUrl(
        fileEntry: FileEntry,
        issue: IssueOperations
    ): String = withContext(Dispatchers.IO) {
        when (fileEntry.storageType) {
            StorageType.global -> {
                (contentService.downloadMetadata(
                    AppInfoKey(),
                    maxRetries = -1 // retry indefinitely
                ) as AppInfo).globalBaseUrl
            }

            StorageType.resource -> {
                (contentService.downloadMetadata(
                    ResourceInfoKey(-1),
                    maxRetries = -1 // retry indefinitely
                ) as ResourceInfo).resourceBaseUrl
            }

            StorageType.issue -> issue.baseUrl
        }
    }
}