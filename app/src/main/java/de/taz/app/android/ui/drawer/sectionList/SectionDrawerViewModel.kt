package de.taz.app.android.ui.drawer.sectionList

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.R
import de.taz.app.android.api.models.Image
import de.taz.app.android.persistence.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SectionDrawerViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val imageRepository = ImageRepository.getInstance(application)

    val navButton = MutableLiveData<Image?>(null)
    val drawerOpen = MutableLiveData(false)

    fun setDefaultDrawerNavButton() {
        val defaultDrawerFileName = getApplication<Application>().resources.getString(R.string.DEFAULT_NAV_DRAWER_FILE_NAME)
        viewModelScope.launch(Dispatchers.IO) {
            navButton.postValue(imageRepository.get(defaultDrawerFileName))
        }
    }


}