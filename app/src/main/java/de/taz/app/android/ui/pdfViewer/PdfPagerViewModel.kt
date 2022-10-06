package de.taz.app.android.ui.pdfViewer

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.DEFAULT_NAV_DRAWER_FILE_NAME
import de.taz.app.android.METADATA_DOWNLOAD_RETRY_INDEFINITELY
import de.taz.app.android.api.models.*
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.monkey.getApplicationScope
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val DEFAULT_NUMBER_OF_PAGES = 29
const val KEY_CURRENT_ITEM = "KEY_CURRENT_ITEM"
const val KEY_HIDE_DRAWER = "KEY_HIDE_DRAWER"

enum class SwipeEvent {
    LEFT, RIGHT
}

class PdfPagerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val log by Log

    private val authHelper = AuthHelper.getInstance(application)
    private val contentService: ContentService =
        ContentService.getInstance(application.applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(application)
    private val articleRepository = ArticleRepository.getInstance(application)
    private val issueRepository = IssueRepository.getInstance(application)
    private val imageRepository = ImageRepository.getInstance(application)

    val issuePublication = MutableLiveData<IssuePublicationWithPages>()
    private val _issueKey = MutableLiveData<IssueKeyWithPages>(null)
    val issueKey = _issueKey

    val userInputEnabled = MutableLiveData(true)
    val requestDisallowInterceptTouchEvent = MutableLiveData(false)
    val hideDrawerLogo = savedStateHandle.getLiveData(KEY_HIDE_DRAWER, false)

    val issueDownloadFailedErrorFlow = MutableStateFlow(false)
    val swipePageFlow = MutableSharedFlow<SwipeEvent>(0)

    private val _currentItem = savedStateHandle.getLiveData<Int>(KEY_CURRENT_ITEM)
    val currentItem = _currentItem as LiveData<Int>

    fun updateCurrentItem(position: Int) {
        viewModelScope.launch {
            updateCurrentItemInternal(position)
        }
    }

    private suspend fun updateCurrentItemInternal(position: Int) = withContext(Dispatchers.Main) {
        if (_currentItem.value != position) {
            _currentItem.value = position

            // Save current position to database to restore later on
            issueKey.value?.let {
                issueRepository.saveLastPagePosition(
                    it.getIssueKey(),
                    position
                )
            }
        }
    }

    private val issueWithPages = MediatorLiveData<IssueWithPages>().apply {
        addSource(issuePublication) { issuePublicationWithPages ->
            viewModelScope.launch {
                // We'll try to download the issues metadata 3 times.
                // If that fails (for example due to missing network) we will emit an error and retry
                // the download indefinitely.
                // TODO (johannes): getting a full issue from the individual db tables takes quite long, we might want to cache it
                val issue = try {
                    issueDownloadFailedErrorFlow.emit(false)
                    downloadIssueMetadata(issuePublicationWithPages, maxRetries = 3)
                } catch (e: CacheOperationFailedException) {
                    // show dialog and retry infinitely
                    issueDownloadFailedErrorFlow.emit(true)
                    downloadIssueMetadata(issuePublicationWithPages)
                }

                // Store the issue metadata
                _issueKey.value = issue.issueKey
                value = issue
            }
        }
    }

    private val downloadedIssue = MediatorLiveData<IssueWithPages>().apply {
        addSource(issueWithPages) { issueWithPages ->
            viewModelScope.launch {

                // Then we start the actual download of the Issue data with the PDF pages.
                // While this will also download the issues metadata it does not return that data.
                // The download will be started on the application scope, so that it can finish even
                // if this ViewModel is destroyed - it will retry indefinitely.
                // We wait (join) until the operations coroutine has finished - note that we don't know if the
                // downloadToCache did succeed or failed: only that the launched coroutine has stopped.
                // TODO (johannes): getting a full issue from the individual db tables takes quite long, we might want to cache it
                getApplicationScope().launch {
                    contentService.downloadToCache(issueWithPages.issueKey)
                }.join()

                // Update the latest page position and the viewDate
                updateCurrentItemInternal(issueWithPages.lastPagePosition ?: 0)
                issueRepository.updateLastViewedDate(issueWithPages)

                // Last we'll get the latest issue entry from the database, so that we will have
                // a correct download date and isDownloaded() can return true.
                val issueStub = issueRepository.getStub(issueWithPages.issueKey)
                if (issueStub != null) {
                    // We will not get the full issue from the database as this requires quite some work/time,
                    // only the latest issue stub to update our metadata with
                    val downloadedIssue = issueWithPages.copyWithMetadata(issueStub)

                    // Finally emit the downloaded issue
                    value = downloadedIssue

                } else {
                    val hint = "Issue that was just downloaded is not found in the database."
                    log.error(hint)
                    Sentry.captureMessage(hint)
                    issueDownloadFailedErrorFlow.emit(true)
                }
            }
        }
    }

    val navButton = MediatorLiveData<Image>().apply {
        viewModelScope.launch {
            postValue(imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME))
        }
    } as LiveData<Image>

    val pdfPageList = MediatorLiveData<List<Page>>().apply {
        addSource(downloadedIssue) { issue ->
            viewModelScope.launch {
                if (issue.isDownloaded(application)) {
                    // as we do not know before downloading where we stored the fileEntry
                    // and the fileEntry storageLocation is in the model - get it freshly from DB
                    postValue(
                        issue.pageList.map {
                            it.copy(pagePdf = requireNotNull(
                                fileEntryRepository.get(it.pagePdf.name)
                            ) { "Refreshing pagePdf fileEntry failed as fileEntry was null" })
                        }
                    )
                }
            }
        }
    }

    fun getAmountOfPdfPages(): Int {
        return pdfPageList.value?.size ?: DEFAULT_NUMBER_OF_PAGES
    }

    fun setUserInputEnabled(enabled: Boolean = true) {
        userInputEnabled.value = enabled
    }

    fun setRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        requestDisallowInterceptTouchEvent.value = disallowIntercept
    }

    fun goToPdfPage(link: String) {
        // it is only possible to go to another page if we are on a regular issue
        // (otherwise we only have the first page)
        if (downloadedIssue.value?.status == IssueStatus.regular) {
            updateCurrentItem(getPositionOfPdf(link))
        }
    }

    private fun getPositionOfPdf(fileName: String): Int {
        return pdfPageList.value?.indexOfFirst { it.pagePdf.name == fileName } ?: 0
    }

    suspend fun getCorrectArticle(link: String): Article? {
        val correctLink = if (downloadedIssue.value?.status == IssueStatus.regular) {
            link
        } else {
            // if we are not on a regular issue all the articles have "public" indication
            // unfortunately it is not delivered via [page.frameList] so we need to modify it here:
            link.replace(".html", ".public.html")
        }
        return articleRepository.get(correctLink)
    }

    val elapsedSubscription = authHelper.status.asFlow()
    val elapsedFormAlreadySent = authHelper.elapsedFormAlreadySent.asFlow()

    private suspend fun downloadIssueMetadata(
        issuePublicationWithPages: IssuePublicationWithPages,
        maxRetries: Int = METADATA_DOWNLOAD_RETRY_INDEFINITELY
    ) = contentService.downloadMetadata(
        issuePublicationWithPages,
        maxRetries = maxRetries
    ) as IssueWithPages
}

