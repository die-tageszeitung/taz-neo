package de.taz.app.android.ui.main

import androidx.lifecycle.*
import de.taz.app.android.api.models.Issue
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.*

class MainDataController : ViewModel(), MainContract.DataController {

    private val selectedIssue = MutableLiveData<Issue?>().apply {
        postValue(null)
    }

    // TODO check whether this works with multiple issues
    private val selectedIssueIsDownloaded: LiveData<Boolean> =
        Transformations.map(selectedIssue) { selectedIssue ->
            runBlocking {
                selectedIssue?.isDownloadedLiveData()?.value
            }
        }

    override fun observeIssue(lifeCycleOwner: LifecycleOwner, newDataBlock: (Issue?) -> (Unit)) {
        selectedIssue.observe(lifeCycleOwner, Observer { issue -> newDataBlock.invoke(issue) })
    }

    override fun observeIssueIsDownloaded(
        lifeCycleOwner: LifecycleOwner, newDataBlock: (Boolean) -> Unit
    ) {
        selectedIssueIsDownloaded.observe(
            lifeCycleOwner,
            Observer { isDownloaded -> newDataBlock.invoke(isDownloaded ?: false) })
    }

    override fun getIssue(): Issue? {
        return selectedIssue.value
    }

    override fun setIssue(issue: Issue) {
        return selectedIssue.postValue(issue)
    }

}
