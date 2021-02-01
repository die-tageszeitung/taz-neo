package de.taz.app.android.ui.pdfViewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import de.taz.app.android.DEFAULT_NAV_DRAWER_FILE_NAME
import de.taz.app.android.api.models.Frame
import de.taz.app.android.api.models.Image
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Log
import io.sentry.core.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class PdfPagerViewModel(
    application: Application,
    val issueKey: IssueKey
) : AndroidViewModel(application) {
    private val log by Log

    val imageRepository = ImageRepository.getInstance(application)
    var dataService: DataService = DataService.getInstance(application.applicationContext)
    var storageService: StorageService = StorageService.getInstance(application.applicationContext)

    val navButton = MutableLiveData<Image?>(null)
    val userInputEnabled = MutableLiveData(true)
    val currentItem = MutableLiveData(0)
    var listOfPdfWithFrameList: List<Pair<File, List<Frame>>> = emptyList()
    var listOfPdfWithTitleAndPagina: List<PdfDrawerItemModel> = emptyList()

    init {
        runBlocking(Dispatchers.IO) {
            val issue = dataService.getIssue(issueKey)

            if (issue == null) {
                val hint = "Couldn't fetch issue $issueKey from dataService!"
                log.warn(hint)
                Sentry.captureMessage(hint)
            } else {
                listOfPdfWithFrameList = issue.pageList.map {
                    storageService.getFile(it.pagePdf)!! to it.frameList!!
                }
                listOfPdfWithTitleAndPagina = issue.pageList.map {
                    PdfDrawerItemModel(
                        storageService.getFile(it.pagePdf)!!,
                        it.title!!,
                        it.pagina!!
                    )
                }
            }
        }

    }

    fun setDefaultDrawerNavButton() {
        viewModelScope.launch(Dispatchers.IO) {
            navButton.postValue(imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME))
        }
    }

    fun toggleViewPagerInput(enabled: Boolean = true) {
        userInputEnabled.value = enabled
    }

    fun goToPdfPage(link: String) {
        currentItem.value = getPositionOfPdf(link)
    }

    private fun getPositionOfPdf(fileName: String): Int {
        return listOfPdfWithFrameList.indexOfFirst { it.first.name == fileName }
    }
}