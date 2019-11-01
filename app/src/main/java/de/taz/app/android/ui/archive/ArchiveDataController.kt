package de.taz.app.android.ui.archive

import androidx.lifecycle.*
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.IssueRepository

class ArchiveDataController: BaseDataController(), ArchiveContract.DataController {

    private val issueLiveData: LiveData<List<IssueStub>> = IssueRepository.getInstance().getAllStubsLiveData()


    override fun getIssueStubs(): List<IssueStub>? {
        return issueLiveData.value
    }

    override fun observeIssues(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (List<IssueStub>?) -> Unit
    ) {
        issueLiveData.observe(lifeCycleOwner,  Observer {
            issues -> observationCallback.invoke(issues) }
        )
    }
}
