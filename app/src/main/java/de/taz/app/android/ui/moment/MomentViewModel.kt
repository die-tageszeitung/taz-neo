package de.taz.app.android.ui.moment

import androidx.lifecycle.*
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Moment
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
    val issueStubLiveData: LiveData<IssueStub?> =
        currentIssueOperationsLiveData.switchMap { issueOperations ->
            liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
                issueOperations?.let {
                    emitSource(
                        issueRepository.getStubLiveData(
                            issueOperations.feedName,
                            issueOperations.date,
                            issueOperations.status
                        )
                    )
                } ?: emit(null)
            }
        }

    val isDownloadedLiveData: LiveData<Boolean> = issueStubLiveData.map { issueStub ->
        issueStub?.dateDownload != null
    }

    val date: String?
        get() = currentIssueOperationsLiveData.value?.date

    val momentLiveData: LiveData<Moment?> =
        currentIssueOperationsLiveData.switchMap { issueOperations ->
            liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
                emit(issueOperations?.let { momentRepository.get(issueOperations) })
            }
        }

    val moment: Moment?
        get() = momentLiveData.value

    val isMomentDownloadedLiveData: LiveData<Boolean> =
        momentLiveData.switchMap { moment ->
            liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
                moment?.let {
                    emitSource(
                        moment.isDownloadedLiveData()
                    )
                } ?: emit(false)
            }
        }

    val isMomentDownloadingLiveData: LiveData<Boolean> =
        momentLiveData.switchMap { moment ->
            liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
                moment?.let {
                    emitSource(
                        moment.isDownloadedOrDownloadingLiveData()
                    )
                } ?: emit(false)
            }
        }

    var issueOperations: IssueOperations?
        set(value) = currentIssueOperationsLiveData.postValue(value)
        get() = currentIssueOperationsLiveData.value


}