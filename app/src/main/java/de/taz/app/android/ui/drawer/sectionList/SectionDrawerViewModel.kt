package de.taz.app.android.ui.drawer.sectionList

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData

class SectionDrawerViewModel(
    application: Application
) : AndroidViewModel(application) {

    val drawerOpen = MutableLiveData(false)

}