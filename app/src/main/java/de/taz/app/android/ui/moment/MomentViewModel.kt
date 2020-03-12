package de.taz.app.android.ui.moment

import androidx.lifecycle.*
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.Issue
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers

class MomentViewModel(
    val issueRepository: IssueRepository = IssueRepository.getInstance(),
    val momentRepository: MomentRepository = MomentRepository.getInstance()
) : ViewModel() {
    private val log by Log

    private var currentIssueOperationsLiveData = MutableLiveData<IssueOperations?>(null)

    val issueLiveData: LiveData<Issue?> =
        currentIssueOperationsLiveData.switchMap { issueOperations ->
            liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
                issueOperations?.let {
                    emitSource(
                        issueRepository.getIssueLiveData(issueOperations)
                    )
                } ?: emit(null)
            }
        }

    val issue: Issue?
        get() = issueLiveData.value

    val isDownloadedLiveData: LiveData<Boolean> = issueLiveData.switchMap { issue ->
        liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
            issue?.let {
                emitSource(issue.isDownloadedLiveData())
            } ?: emit(false)
        }
    }

    val isMomentDownloadedLiveData: LiveData<Boolean> =
        currentIssueOperationsLiveData.switchMap { issueOperations ->
            liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
                issueOperations?.let {
                    emitSource(
                        momentRepository.get(issueOperations)?.isDownloadedLiveData()
                            ?: MutableLiveData(false)
                    )
                } ?: emit(false)
            }
        }

    val isMomentDownloadingLiveData: LiveData<Boolean> =
        currentIssueOperationsLiveData.switchMap { issueOperations ->
            liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
                issueOperations?.let {
                    emitSource(
                        momentRepository.get(issueOperations)?.isDownloadedOrDownloadingLiveData()
                            ?: MutableLiveData(false)
                    )
                } ?: emit(false)
            }
        }

    fun setIssueOperations(issueStub: IssueOperations) {
        currentIssueOperationsLiveData.postValue(issueStub)
    }

}