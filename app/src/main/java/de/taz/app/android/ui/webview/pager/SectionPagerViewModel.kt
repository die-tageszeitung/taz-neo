package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.*
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SectionPagerViewModel : ViewModel() {

    val sectionKeyLiveData = MutableLiveData<String?>(null)
    var sectionKey
        get() = sectionKeyLiveData.value
        set(value) {
            sectionKeyLiveData.value = value
        }

    val issueFeedNameLiveData = MutableLiveData<String?>(null)
    var issueFeedName
        get() = issueFeedNameLiveData.value
        set(value) {
            issueFeedNameLiveData.value = value
        }

    val issueDateLiveData = MutableLiveData<String?>(null)
    var issueDate
        get() = issueDateLiveData.value
        set(value) {
            issueDateLiveData.value = value
        }


    val issueStatusLiveData = MutableLiveData<IssueStatus?>(null)
    var issueStatus
        get() = issueStatusLiveData.value
        set(value) {
            issueStatusLiveData.value = value
        }


    val currentPositionLiveData = MutableLiveData(0)
    var currentPosition
        get() = currentPositionLiveData.value
        set(value) {
            currentPositionLiveData.value = value
        }

    val issueLiveData: LiveData<Issue?> = MediatorLiveData<Issue?>().apply {
            addSource(sectionKeyLiveData) { getIssueBySection(this) }
            addSource(issueDateLiveData) { getIssue(this) }
            addSource(issueFeedNameLiveData) { getIssue(this) }
            addSource(issueStatusLiveData) { getIssue(this) }
        }
    val issue = issueLiveData.value

    private fun getIssue(mediatorLiveData: MediatorLiveData<Issue?>) {
        runIfNotNull(
            issueDate,
            issueFeedName,
            issueStatus
        ) { issueDate, issueFeedName, issueStatus ->
            CoroutineScope(Dispatchers.IO).launch {
                mediatorLiveData.postValue(
                    IssueRepository.getInstance().getIssue(issueFeedName, issueDate, issueStatus)
                )
            }
        }
    }

    private fun getIssueBySection(mediatorLiveData: MediatorLiveData<Issue?>) {
        CoroutineScope(Dispatchers.IO).launch {
            sectionKey?.let { sectionKey ->
                val issue = IssueRepository.getInstance().getIssueForSection(sectionKey)
                currentPositionLiveData.postValue(issue.sectionList.indexOfFirst { it.sectionFileName == sectionKey })
                mediatorLiveData.postValue(issue)
            }
        }
    }

}