package de.taz.app.android.ui.main

import androidx.lifecycle.*
import de.taz.app.android.api.models.Issue
import de.taz.app.android.base.BaseDataController
import kotlinx.coroutines.*

open class MainDataController : BaseDataController(), MainContract.DataController {

    private val selectedIssue = MutableLiveData<Issue?>().apply {
        postValue(null)
    }

    private val selectedIssueIsDownloaded: LiveData<Boolean> =
        Transformations.map(selectedIssue) { selectedIssue ->
            runBlocking {
                selectedIssue?.isDownloadedLiveData()?.value
            }
        }

    override fun observeIssue(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (Issue?) -> (Unit)
    ) {
        selectedIssue.observe(
            lifeCycleOwner,
            Observer { issue -> observationCallback.invoke(issue) })
    }

    override fun observeIssueIsDownloaded(
        lifeCycleOwner: LifecycleOwner, observationCallback: (Boolean) -> Unit
    ) {
        selectedIssueIsDownloaded.observe(
            lifeCycleOwner,
            Observer { isDownloaded -> observationCallback.invoke(isDownloaded ?: false) })
    }

    override fun getIssue(): Issue? {
        return selectedIssue.value
    }

    override fun setIssue(issue: Issue) {
        return selectedIssue.postValue(issue)
    }

}
