package de.taz.app.android.ui.drawer.sectionList

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.models.Issue
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.*

class SelectedIssueViewModel : ViewModel() {

    val selectedIssue = MutableLiveData<Issue?>().apply {
        CoroutineScope(Dispatchers.IO).launch {
            postValue(IssueRepository.getInstance().getLatestIssue())
        }
    }

    // TODO check whether this works with multiple issues
    val selectedIssueDownloaded: LiveData<Boolean> = Transformations.map(selectedIssue) {
         selectedIssue -> runBlocking {
            selectedIssue?.isDownloadedLiveData()?.value
        }
    }
}
