package de.taz.app.android.ui.pdfViewer

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.DEFAULT_NAV_DRAWER_FILE_NAME
import de.taz.app.android.TazApplication
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueWithPages
import de.taz.app.android.api.models.Page
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
    private val dataService = DataService.getInstance(application)
    private val contentService: ContentService =
        ContentService.getInstance(application.applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(application)
    private val issueRepository = IssueRepository.getInstance(application)
    private val imageRepository = ImageRepository.getInstance(application)

    val issuePublication = MutableLiveData<IssuePublicationWithPages>()
    val issueKey = MediatorLiveData<IssueKeyWithPages>().apply {
        addSource(issuePublication) {
            viewModelScope.launch {
                val issue = contentService.downloadMetadata(it) as IssueWithPages
                postValue(issue.issueKey)
            }
        }
    }
    val userInputEnabled = MutableLiveData(true)
    val requestDisallowInterceptTouchEvent = MutableLiveData(false)
    val hideDrawerLogo = savedStateHandle.getLiveData(KEY_HIDE_DRAWER, false)

    val issueDownloadFailedErrorFlow = MutableStateFlow(false)
    val swipePageFlow = MutableSharedFlow<SwipeEvent>(0)

    private val _currentItem = savedStateHandle.getLiveData<Int>(KEY_CURRENT_ITEM)
    val currentItem = _currentItem as LiveData<Int>

    fun updateCurrentItem(position: Int) {
        if (_currentItem.value != position) {
            _currentItem.postValue(position)

            // Save current position to database to restore later on
            viewModelScope.launch {
                issueKey.value?.let {
                    dataService.saveLastPageOnIssue(
                        it.getIssueKey(),
                        position
                    )
                }
            }
        }
    }

    private val issue = MediatorLiveData<IssueWithPages>().apply {
        addSource(issuePublication) { issuePublicationWithPages ->
            suspend fun downloadMetaData(maxRetries: Int = -1) = contentService.downloadMetadata(
                issuePublicationWithPages,
                maxRetries = maxRetries
            ) as IssueWithPages

            viewModelScope.launch {
                val issue = try {
                    issueDownloadFailedErrorFlow.emit(false)
                    downloadMetaData(maxRetries = 3)
                } catch (e: CacheOperationFailedException) {
                    // show dialog and retry infinitely
                    issueDownloadFailedErrorFlow.emit(true)
                    downloadMetaData()
                }

                // TODO move?
                // Get latest shown page and set it before setting the issue
                updateCurrentItem(issue.lastPagePosition ?: 0)

                issueRepository.updateLastViewedDate(issue)
                postValue(issue)
                applicationScope.launch {
                    contentService.downloadToCache(issuePublicationWithPages)
                }.join()
                postValue(issue)
            }
        }
    }

    val navButton = MediatorLiveData<Image>().apply {
        viewModelScope.launch(Dispatchers.IO) {
            postValue(imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME))
        }
    } as LiveData<Image>

    val pdfPageList = MediatorLiveData<List<Page>>().apply {
        addSource(issue) { issue ->
            viewModelScope.launch(Dispatchers.IO) {
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
        updateCurrentItem(getPositionOfPdf(link))
    }

    private fun getPositionOfPdf(fileName: String): Int {
        return pdfPageList.value?.indexOfFirst { it.pagePdf.name == fileName } ?: 0
    }

    private val applicationScope by lazy {
        (application as TazApplication).applicationScope
    }
}