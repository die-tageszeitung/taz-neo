package de.taz.app.android.ui.webview.pager

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SectionPagerViewModel(application: Application) : AndroidViewModel(application) {

    val currentPositionLiveData = MutableLiveData(0)
    val currentPosition
        get() = currentPositionLiveData.value ?: 0

}