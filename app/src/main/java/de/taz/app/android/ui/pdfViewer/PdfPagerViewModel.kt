package de.taz.app.android.ui.pdfViewer

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.DEFAULT_NAV_DRAWER_FILE_NAME
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.PageType
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val DEFAULT_NUMBER_OF_PAGES = 29

class PdfPagerViewModel(
    application: Application,
    val issueKey: IssueKey
) : AndroidViewModel(application) {

    val imageRepository = ImageRepository.getInstance(application.applicationContext)
    var dataService: DataService = DataService.getInstance(application.applicationContext)
    var storageService: StorageService = StorageService.getInstance(application.applicationContext)
    private val toastHelper = ToastHelper.getInstance(application.applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance()

    val navButton = MutableLiveData<Image?>(null)
    val userInputEnabled = MutableLiveData(true)
    val hideDrawerLogo= MutableLiveData(false)
    val currentItem = MutableLiveData(0)
    var activePosition = MutableLiveData(0)
    var pdfDataList: MutableLiveData<List<PdfPageList>> = MutableLiveData(emptyList())

    private val log by Log

    init {

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
            dataService.ensureDownloaded(
                collection = issue,
                onConnectionFailure = { onConnectionFailure() }
            )

            if (issue.isDownloaded()) {
                pdfDataList.postValue(issue.pageList.map {
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

    fun getAmountOfPdfPages() : Int {
        return pdfDataList.value?.size ?: DEFAULT_NUMBER_OF_PAGES
    }

    fun setDefaultDrawerNavButton() {
        viewModelScope.launch(Dispatchers.IO) {
            navButton.postValue(imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME))
        }
    }

    fun setUserInputEnabled(enabled: Boolean = true) {
        userInputEnabled.value = enabled
    }

    fun goToPdfPage(link: String) {
        currentItem.value = getPositionOfPdf(link)
    }

    private fun getPositionOfPdf(fileName: String): Int {
        return pdfDataList.value?.indexOfFirst { it.pdfFile.name == fileName } ?: 0
    }
}