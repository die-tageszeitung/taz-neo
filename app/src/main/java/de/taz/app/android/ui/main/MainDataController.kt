package de.taz.app.android.ui.main

import androidx.lifecycle.*
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseDataController
import kotlinx.coroutines.*

open class MainDataController : BaseDataController(), MainContract.DataController {

    private val selectedIssue = MutableLiveData<IssueStub?>().apply {
        postValue(null)
    }

    override fun observeIssueStub(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (IssueStub?) -> (Unit)
    ) {
        selectedIssue.observe(
            lifeCycleOwner,
            Observer { issueStub -> observationCallback.invoke(issueStub) })
    }

    override fun getIssueStub(): IssueStub? {
        return selectedIssue.value
    }

    override fun setIssueStub(issueStub: IssueStub) {
        return selectedIssue.postValue(issueStub)
    }

}
