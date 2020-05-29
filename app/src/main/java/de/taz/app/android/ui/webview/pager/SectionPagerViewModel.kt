package de.taz.app.android.ui.webview.pager

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

class SectionPagerViewModel : ViewModel() {

    val sectionKeyLiveData = MutableLiveData<String?>(null)
    val sectionKey
        get() = sectionKeyLiveData.value

    val issueFeedNameLiveData = MutableLiveData<String?>(null)
    val issueFeedName
        get() = issueFeedNameLiveData.value

    val issueDateLiveData = MutableLiveData<String?>(null)
    val issueDate
        get() = issueDateLiveData.value

    val issueStatusLiveData = MutableLiveData<IssueStatus?>(null)
    val issueStatus
        get() = issueStatusLiveData.value

    val issueOperationsLiveData = MutableLiveData<IssueOperations?>(null)

    val currentPositionLiveData = MutableLiveData(0)
    val currentPosition
        get() = currentPositionLiveData.value

    val sectionStubListLiveData: LiveData<List<SectionStub>> =
        MediatorLiveData<List<SectionStub>>().apply {
            addSource(sectionKeyLiveData) { getSectionListBySection() }
            addSource(issueDateLiveData) { getSectionListByIssue(this) }
            addSource(issueFeedNameLiveData) { getSectionListByIssue(this) }
            addSource(issueStatusLiveData) { getSectionListByIssue(this) }
        }

    private fun getSectionListByIssue(mediatorLiveData: MediatorLiveData<List<SectionStub>>) {
        runIfNotNull(
            issueDate,
            issueFeedName,
            issueStatus
        ) { issueDate, issueFeedName, issueStatus ->
            CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO).launch {
                val sections = SectionRepository.getInstance().getSectionStubsForIssue(
                    issueFeedName, issueDate, issueStatus
                )
                sectionKeyLiveData.value?.let { sectionFileName ->
                    sectionKeyLiveData.postValue(null)
                    val index = sections.indexOfFirst { it.sectionFileName == sectionFileName }
                    if (index > 0) {
                        currentPositionLiveData.postValue(index)
                    }
                }

                if (issueOperationsLiveData.value == null) {
                    issueOperationsLiveData.postValue(
                        IssueRepository.getInstance().getStub(
                            issueFeedName, issueDate, issueStatus
                        )
                    )
                }

                mediatorLiveData.postValue(sections)
            }
        }
    }

    private fun getSectionListBySection() {
        CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO).launch {
            sectionKeyLiveData.value?.let { sectionFileName ->
                val issueStub =
                    IssueRepository.getInstance().getIssueStubForSection(sectionFileName)
                issueFeedNameLiveData.postValue(issueStub.feedName)
                issueStatusLiveData.postValue(issueStub.status)
                issueDateLiveData.postValue(issueStub.date)
            }
        }
    }

}