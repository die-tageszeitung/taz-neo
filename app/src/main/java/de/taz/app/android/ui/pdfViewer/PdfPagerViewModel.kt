package de.taz.app.android.ui.pdfViewer

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.DEFAULT_NAV_DRAWER_FILE_NAME
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueWithPages
import de.taz.app.android.api.models.Page
import de.taz.app.android.content.ContentService
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueKeyWithPages
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val DEFAULT_NUMBER_OF_PAGES = 29
const val KEY_CURRENT_ITEM = "KEY_CURRENT_ITEM"
const val KEY_HIDE_DRAWER = "KEY_HIDE_DRAWER"

class PdfPagerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val dataService = DataService.getInstance(application)
    private val contentService: ContentService = ContentService.getInstance(application.applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(application)
    private val imageRepository = ImageRepository.getInstance(application)


    val issueKey = MutableLiveData<IssueKeyWithPages>()
    val navButton = MutableLiveData<Image?>(null)
    val userInputEnabled = MutableLiveData(true)
    val requestDisallowInterceptTouchEvent = MutableLiveData(false)
    val hideDrawerLogo = savedStateHandle.getLiveData(KEY_HIDE_DRAWER, false)

    private val _currentItem = savedStateHandle.getLiveData<Int>(KEY_CURRENT_ITEM)
    val currentItem = _currentItem as LiveData<Int>

    fun updateCurrentItem(position: Int) {
        if (_currentItem.value != position) {
            _currentItem.postValue(position)

            // Save current position to database to restore later on
            CoroutineScope(Dispatchers.IO).launch {
                issueKey.value?.let {
                    dataService.saveLastPageOnIssue(
                        it.getIssueKey(),
                        position
                    )
                }
            }
        }
    }

    private val log by Log

    val pdfPageList = MediatorLiveData<List<Page>>().apply {
        addSource(issueKey) { issueKey ->
            viewModelScope.launch(Dispatchers.IO) {
                val issue = dataService.getIssue(
                    IssuePublication(issueKey),
                    retryOnFailure = true
                )
                // Get latest shown page and set it before setting the issue
                updateCurrentItem(issue.lastPagePosition ?: 0)
                val pdfIssue = IssueWithPages(issue)
                // Update view models' issueKey if it has a different status (maybe it is a demo/regular issue)
                if (pdfIssue.status != issueKey.status) {
                    this@PdfPagerViewModel.issueKey.postValue(pdfIssue.issueKey)
                }
                contentService.downloadToCacheIfNotPresent(pdfIssue.issueKey)

                navButton.postValue(
                    imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME)
                )

                if (pdfIssue.isDownloaded(application)) {
                    // as we do not know before downloading where we stored the fileEntry
                    // and the fileEntry storageLocation is in the model - get it freshly from DB
                    postValue(
                        pdfIssue.pageList.map {
                            it.copy(pagePdf = requireNotNull(
                                fileEntryRepository.get(it.pagePdf.name)
                            ) { "Refreshing pagePdf fileEntry failed as fileEntry was null" })
                        }
                    )
                } else {
                    val hint = "Something went wrong downloading issue with its pdfs"
                    log.warn(hint)
                    Sentry.captureMessage(hint)
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

}