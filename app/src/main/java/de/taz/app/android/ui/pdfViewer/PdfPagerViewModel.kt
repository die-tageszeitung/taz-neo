package de.taz.app.android.ui.pdfViewer

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueWithPages
import de.taz.app.android.api.models.PageType
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueKeyWithPages
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val DEFAULT_NUMBER_OF_PAGES = 29
const val KEY_CURRENT_ITEM = "KEY_CURRENT_ITEM"
const val KEY_HIDE_DRAWER = "KEY_HIDE_DRAWER"

class PdfPagerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    var dataService: DataService = DataService.getInstance(application.applicationContext)
    var storageService: StorageService = StorageService.getInstance(application.applicationContext)
    private val toastHelper = ToastHelper.getInstance(application.applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance()

    val issueKey = MutableLiveData<IssueKeyWithPages>()
    val navButton = MutableLiveData<Image?>(null)
    val userInputEnabled = MutableLiveData(true)
    val hideDrawerLogo = savedStateHandle.getLiveData<Boolean>(KEY_HIDE_DRAWER, false)
    val currentItem = savedStateHandle.getLiveData<Int>(KEY_CURRENT_ITEM)

    private val log by Log

    val pdfPageList = MediatorLiveData<List<PdfPageList>>().apply {
        addSource(issueKey) { issueKey ->
            var noConnectionShown = false
            fun onConnectionFailure() {
                if (!noConnectionShown) {
                    viewModelScope.launch {
                        toastHelper.showNoConnectionToast()
                        noConnectionShown = true
                    }
                }
            }
            viewModelScope.launch(Dispatchers.IO) {
                val issue = dataService.getIssue(
                    IssuePublication(issueKey),
                    retryOnFailure = true,
                    onConnectionFailure = { onConnectionFailure() }
                )
                val pdfIssue = IssueWithPages(issue)

                dataService.ensureDownloaded(
                    pdfIssue,
                    onConnectionFailure = { onConnectionFailure() }
                )

                if (pdfIssue.isDownloaded()) {
                    postValue(pdfIssue.pageList.map {
                        val file = fileEntryRepository.get(it.pagePdf.name)?.let { fileEntry ->
                            storageService.getFile(fileEntry)
                        }
                        PdfPageList(
                            file!!,
                            it.frameList ?: emptyList(),
                            it.title ?: "",
                            it.pagina ?: "",
                            it.type ?: PageType.left
                        )
                    })
                } else {
                    val hint = "Something went wrong downloading issue with its pdfs"
                    log.warn(hint)
                    Sentry.captureMessage(hint)
                }
            }
        }
    }

    fun getAmountOfPdfPages() : Int {
        return pdfPageList.value?.size ?: DEFAULT_NUMBER_OF_PAGES
    }

    fun setUserInputEnabled(enabled: Boolean = true) {
        userInputEnabled.value = enabled
    }

    fun goToPdfPage(link: String) {
        currentItem.value = getPositionOfPdf(link)
    }

    private fun getPositionOfPdf(fileName: String): Int {
        return pdfPageList.value?.indexOfFirst { it.pdfFile.name == fileName } ?: 0
    }
}