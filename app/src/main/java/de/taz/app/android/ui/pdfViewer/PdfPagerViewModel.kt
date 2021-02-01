package de.taz.app.android.ui.pdfViewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.DEFAULT_NAV_DRAWER_FILE_NAME
import de.taz.app.android.api.models.Frame
import de.taz.app.android.api.models.Image
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.StorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class PdfPagerViewModel (
    application: Application
) : AndroidViewModel(application) {
    private val imageRepository = ImageRepository.getInstance(application)

    val navButton = MutableLiveData<Image?>(null)
    val drawerOpen = MutableLiveData(false)
    val viewPager = MutableLiveData<ViewPager2>()

    fun setViewPager(viewPager2: ViewPager2) {
        viewPager.value = viewPager2
    }

    fun setViewPagerUserInputEnabled(isEnabled: Boolean =  true) {
        viewPager.value?.isUserInputEnabled = isEnabled
    }

    fun setDefaultDrawerNavButton() {
        viewModelScope.launch(Dispatchers.IO) {
            navButton.postValue(imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME))
        }
    }

}