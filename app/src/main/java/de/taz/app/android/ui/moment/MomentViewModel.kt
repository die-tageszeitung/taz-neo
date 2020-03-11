package de.taz.app.android.ui.moment

import androidx.lifecycle.*
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.Issue
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class MomentViewModel(
    val issueRepository: IssueRepository = IssueRepository.getInstance()
) : ViewModel() {
    private val log by Log

    private var currentIssueOperationsLiveData = MutableLiveData<IssueOperations?>(null)

    val issueLiveData: LiveData<Issue?> =
        Transformations.switchMap(
            Transformations.distinctUntilChanged(
                currentIssueOperationsLiveData
            )
        ) { issueOperations ->
            runBlocking(Dispatchers.IO) {
                issueOperations?.let {
                    issueRepository.getIssueLiveData(issueOperations)
                } ?: MutableLiveData<Issue?>(null)
            }
        }

    val issue: Issue?
        get() = issueLiveData.value

    val isDownloadedLiveData: LiveData<Boolean> =
        Transformations.switchMap(Transformations.distinctUntilChanged(issueLiveData)) { issue ->
            issue?.isDownloadedLiveData() ?: MutableLiveData(false)
        }

    val isMomentDownloadedLiveData: LiveData<Boolean> =
        Transformations.switchMap(issueLiveData) { issue ->
            issue?.moment?.isDownloadedLiveData() ?: MutableLiveData(false)
        }

    val isMomentDownloadingLiveData: LiveData<Boolean> =
        Transformations.switchMap(issueLiveData) { issue ->
            issue?.moment?.isDownloadedOrDownloadingLiveData() ?: MutableLiveData(false)
        }

    fun setIssueOperations(issueStub: IssueOperations) {
        currentIssueOperationsLiveData.postValue(issueStub)
    }

}