package de.taz.app.android.ui.main

import androidx.lifecycle.*
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.base.BaseDataController

@Mockable
class MainDataController : BaseDataController(), MainContract.DataController {

    private val selectedIssue = MutableLiveData<IssueOperations?>().apply {
        postValue(null)
    }

    override fun observeIssueStub(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (IssueOperations?) -> (Unit)
    ) {
        selectedIssue.observe(
            lifeCycleOwner,
            Observer { issueStub -> observationCallback.invoke(issueStub) })
    }

    override fun getIssueStub(): IssueOperations? {
        return selectedIssue.value
    }

    override fun setIssueOperations(issueOperations: IssueOperations?) {
        return selectedIssue.postValue(issueOperations)
    }

}
