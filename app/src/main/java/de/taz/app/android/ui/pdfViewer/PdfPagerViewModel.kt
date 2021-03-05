package de.taz.app.android.ui.pdfViewer

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.DEFAULT_NAV_DRAWER_FILE_NAME
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.PageType
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PdfPagerViewModel(
    application: Application,
    val issueKey: IssueKey
) : AndroidViewModel(application) {

    val imageRepository = ImageRepository.getInstance(application.applicationContext)
    var dataService: DataService = DataService.getInstance(application.applicationContext)
    var storageService: StorageService = StorageService.getInstance(application.applicationContext)
    private val toastHelper = ToastHelper.getInstance(application)

    val navButton = MutableLiveData<Image?>(null)
    val userInputEnabled = MutableLiveData(true)
    val currentItem = MutableLiveData(0)
    var activePosition = MutableLiveData(0)
    var pdfDataListModel: MutableLiveData<List<PdfPageListModel>> = MutableLiveData(emptyList())
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
                onConnectionFailure = {
                    onConnectionFailure()
                })

            dataService.ensureDownloaded(
                issue,
                skipIntegrityCheck = false,
                onConnectionFailure = ::onConnectionFailure
            )
            // TODO check better if is downloaded and/or wait accordingly
            if (issue.isDownloaded() && issue.pageList.first().pagePdf.storageLocation != StorageLocation.NOT_STORED) {
                pdfDataListModel.postValue(issue.pageList.map {
                    PdfPageListModel(
                        storageService.getFile(it.pagePdf)!!,
                        it.frameList ?: emptyList(),
                        it.title ?: "",
                        it.pagina ?: "",
                        it.type ?: PageType.left
                    )
                })
            }
        }
    }

    fun getAmountOfPdfPages() : Int {
        return pdfDataListModel.value?.size ?: 29
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
        return pdfDataListModel.value?.indexOfFirst { it.pdfFile.name == fileName } ?: 0
    }
}