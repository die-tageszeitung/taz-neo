package de.taz.app.android.ui.webview.pager

import android.app.Application
import androidx.lifecycle.*

class SectionPagerViewModel(application: Application) : AndroidViewModel(application) {

    val currentPositionLiveData = MutableLiveData(0)
    val currentPosition
        get() = currentPositionLiveData.value ?: 0

}